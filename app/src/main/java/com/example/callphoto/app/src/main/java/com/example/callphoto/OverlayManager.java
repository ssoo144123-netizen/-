package com.example.callphoto;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;

public class OverlayManager {

    public static final String PREFS = "callphoto";
    public static final String KEY_BOTTOM_PERCENT = "bottom_percent";
    public static final String KEY_SHOW_BUTTONS = "show_buttons";
    public static final int DEFAULT_BOTTOM_PERCENT = 30;

    private static View photoView;
    private static View buttonView;
    private static WindowManager windowManager;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static synchronized void show(Context context, String number) {
        hide();
        final Context app = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(app)) return;

        String name = null;
        Bitmap bitmap = null;
        try {
            Uri lookup = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = app.getContentResolver().query(lookup, new String[]{
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        name = c.getString(0);
                        String photo = c.getString(1);
                        if (photo == null) photo = c.getString(2);
                        if (photo != null) bitmap = decode(app, Uri.parse(photo));
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }

        if (bitmap == null) return; // مخاطب عکس ندارد

        windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);

        android.content.SharedPreferences prefs =
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int bottomPercent = prefs.getInt(KEY_BOTTOM_PERCENT, DEFAULT_BOTTOM_PERCENT);
        boolean showButtons = prefs.getBoolean(KEY_SHOW_BUTTONS, true);

        final int height = dm.heightPixels * (100 - bottomPercent) / 100;
        final int barHeight = (int) (110 * dm.density);

        // ----- عکس -----
        FrameLayout root = new FrameLayout(app);
        root.setBackgroundColor(Color.BLACK);

        ImageView image = new ImageView(app);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(bitmap);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView label = new TextView(app);
        label.setText(name != null ? name : number);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(8, 0, 0, Color.BLACK);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.bottomMargin = (int) (24 * dm.density) + (showButtons ? barHeight : 0);
        root.addView(label, lp);

        // ----- دکمه‌ها -----
        View bar = showButtons ? buildButtons(app, dm.density) : null;
        final int barY = Math.max(0, height - barHeight);

        int[] types = Build.VERSION.SDK_INT >= 26
                ? new int[]{WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY}
                : new int[]{2010 /* TYPE_SYSTEM_ERROR */, 2007 /* TYPE_PRIORITY_PHONE */,
                        WindowManager.LayoutParams.TYPE_PHONE};

        for (int t : types) {
            if (attach(root, height, 0, false, t)) {
                photoView = root;
                if (bar != null && attach(bar, barHeight, barY, true, t)) {
                    buttonView = bar;
                }
                final View r = root;
                final View b = bar;
                final int tt = t;
                // چند لحظه بعد دوباره اضافه می‌کنیم تا حتماً بالای صفحه تماس باشد
                handler.postDelayed(() -> {
                    synchronized (OverlayManager.class) {
                        if (photoView == r) {
                            removeQuietly(r);
                            attach(r, height, 0, false, tt);
                            if (b != null && buttonView == b) {
                                removeQuietly(b);
                                attach(b, barHeight, barY, true, tt);
                            }
                        }
                    }
                }, 700);
                break;
            }
        }
    }

    public static synchronized void hide() {
        removeQuietly(photoView);
        removeQuietly(buttonView);
        photoView = null;
        buttonView = null;
    }

    private static void removeQuietly(View v) {
        if (v != null && windowManager != null) {
            try {
                windowManager.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean attach(View v, int height, int y, boolean touchable, int type) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, height, type, flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.y = y;
        try {
            windowManager.addView(v, params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static View buildButtons(final Context app, float density) {
        LinearLayout bar = new LinearLayout(app);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Color.TRANSPARENT);

        int size = (int) (76 * density);
        int margin = (int) (36 * density);

        TextView reject = circle(app, "رد", Color.parseColor("#E53935"));
        reject.setOnClickListener(v -> {
            CallControl.reject(app);
            hide();
        });
        TextView answer = circle(app, "پاسخ", Color.parseColor("#43A047"));
        answer.setOnClickListener(v -> {
            CallControl.answer(app);
            hide();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(margin, 0, margin, 0);
        bar.addView(reject, lp);
        bar.addView(answer, new LinearLayout.LayoutParams(lp));
        return bar;
    }

    private static TextView circle(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        tv.setBackground(bg);
        return tv;
    }

    private static Bitmap decode(Context ctx, Uri uri) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in = cr.openInputStream(uri);
        if (in == null) return null;
        try {
            BitmapFactory.decodeStream(in, null, bounds);
        } finally {
            in.close();
        }

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= dm.widthPixels
                && bounds.outHeight / (sample * 2) >= dm.heightPixels) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        in = cr.openInputStream(uri);
        if (in == null) return null;
        try {
            return BitmapFactory.decodeStream(in, null, opts);
        } finally {
            in.close();
        }
    }
}
