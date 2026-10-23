package com.example.callphoto;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.widget.TextView;

import java.io.InputStream;

public class OverlayManager {

    public static final String PREFS = "callphoto";
    public static final String KEY_BOTTOM_PERCENT = "bottom_percent";
    public static final int DEFAULT_BOTTOM_PERCENT = 30;

    private static View overlay;
    private static WindowManager windowManager;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static synchronized void show(Context context, String number) {
        hide();
        Context app = context.getApplicationContext();

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

        if (bitmap == null) return; // مخاطب عکس ندارد، چیزی نشان نده

        windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);

        int bottomPercent = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_BOTTOM_PERCENT, DEFAULT_BOTTOM_PERCENT);
        int height = dm.heightPixels * (100 - bottomPercent) / 100;

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
        lp.bottomMargin = (int) (24 * dm.density);
        root.addView(label, lp);

        int[] types = Build.VERSION.SDK_INT >= 26
                ? new int[]{WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY}
                : new int[]{2010 /* TYPE_SYSTEM_ERROR */, 2007 /* TYPE_PRIORITY_PHONE */,
                        WindowManager.LayoutParams.TYPE_PHONE};

        for (int t : types) {
            if (attach(root, height, t)) {
                overlay = root;
                final View r = root;
                final int h = height;
                final int tt = t;
                // چند لحظه بعد دوباره اضافه می‌کنیم تا حتماً بالای صفحه تماس باشد
                handler.postDelayed(() -> {
                    synchronized (OverlayManager.class) {
                        if (overlay == r) {
                            try {
                                windowManager.removeView(r);
                            } catch (Exception ignored) {
                            }
                            attach(r, h, tt);
                        }
                    }
                }, 700);
                break;
            }
        }
    }

    private static boolean attach(View v, int height, int type) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(v, params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized void hide() {
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
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
