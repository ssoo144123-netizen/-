package com.example.callphoto;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;

import java.lang.reflect.Method;

public class CallControl {

    /** رد کردن تماس */
    public static void reject(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
                tm.endCall();
                return;
            }
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            Method getIt = tm.getClass().getDeclaredMethod("getITelephony");
            getIt.setAccessible(true);
            Object it = getIt.invoke(tm);
            Method end = it.getClass().getMethod("endCall");
            end.setAccessible(true);
            end.invoke(it);
        } catch (Throwable ignored) {
        }
    }

    /** جواب دادن به تماس */
    public static void answer(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
                tm.acceptRingingCall();
                return;
            }
        } catch (Throwable ignored) {
        }

        // اندروید قدیمی: شبیه‌سازی دکمه هندزفری
        try {
            sendHeadsetHook(c, KeyEvent.ACTION_DOWN);
            sendHeadsetHook(c, KeyEvent.ACTION_UP);
        } catch (Throwable ignored) {
        }
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_HEADSETHOOK, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_HEADSETHOOK, 0));
        } catch (Throwable ignored) {
        }
    }

    private static void sendHeadsetHook(Context c, int action) {
        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, KeyEvent.KEYCODE_HEADSETHOOK));
        c.sendOrderedBroadcast(i, "android.permission.CALL_PRIVILEGED");
    }
}
