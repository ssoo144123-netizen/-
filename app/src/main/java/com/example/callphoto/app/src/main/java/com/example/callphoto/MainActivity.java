package com.example.callphoto;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("عکس تمام‌صفحه مخاطب هنگام تماس");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView help = new TextView(this);
        help.setText("\nمراحل:\n۱. دکمه «اجازه‌ها» را بزن و هر دو مجوز را تأیید کن.\n"
                + "۲. دکمه «نمایش روی برنامه‌های دیگر» را بزن و برای این برنامه فعالش کن.\n"
                + "۳. اگر دکمه‌های جواب/رد تماس زیر عکس پنهان شدند، اسلایدر پایین را بیشتر کن.\n");
        layout.addView(help);

        status = new TextView(this);
        status.setTextSize(16);
        layout.addView(status);

        Button perms = new Button(this);
        perms.setText("۱. اجازه‌ها (مخاطبین و تلفن)");
        perms.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                java.util.ArrayList<String> list = new java.util.ArrayList<>();
                list.add(Manifest.permission.READ_PHONE_STATE);
                list.add(Manifest.permission.READ_CONTACTS);
                list.add(Manifest.permission.READ_CALL_LOG);
                list.add(Manifest.permission.CALL_PHONE);
                if (Build.VERSION.SDK_INT >= 26) {
                    list.add(Manifest.permission.ANSWER_PHONE_CALLS);
                }
                requestPermissions(list.toArray(new String[0]), 1);
            }
        });
        layout.addView(perms);

        Button overlayBtn = new Button(this);
        overlayBtn.setText("۲. نمایش روی برنامه‌های دیگر");
        overlayBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        layout.addView(overlayBtn);

        final SharedPreferences prefs = getSharedPreferences(OverlayManager.PREFS, MODE_PRIVATE);
        int saved = prefs.getInt(OverlayManager.KEY_BOTTOM_PERCENT,
                OverlayManager.DEFAULT_BOTTOM_PERCENT);

        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("نمایش دکمه‌های پاسخ و رد روی عکس");
        cb.setChecked(prefs.getBoolean(OverlayManager.KEY_SHOW_BUTTONS, true));
        cb.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(OverlayManager.KEY_SHOW_BUTTONS, checked).apply());
        layout.addView(cb);

        final TextView seekLabel = new TextView(this);
        seekLabel.setText("\nفضای آزاد پایین صفحه (برای دکمه‌های تماس): " + saved + "٪");
        layout.addView(seekLabel);

        SeekBar seek = new SeekBar(this);
        seek.setMax(60);
        seek.setProgress(saved);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                seekLabel.setText("\nفضای آزاد پایین صفحه (برای دکمه‌های تماس): " + p + "٪");
                prefs.edit().putInt(OverlayManager.KEY_BOTTOM_PERCENT, p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seek);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean perms = Build.VERSION.SDK_INT < 23
                || (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED);
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        status.setText("اجازه‌ها: " + (perms ? "✅" : "❌")
                + "\nنمایش روی برنامه‌ها: " + (overlay ? "✅" : "❌") + "\n");
    }
}
