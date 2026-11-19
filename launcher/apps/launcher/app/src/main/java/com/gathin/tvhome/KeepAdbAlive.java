package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Each time Home shows: turns network adb back on (this firmware switches it off at every power-up) and,
 * through {@link LocalAdb}, restarts the Shizuku server that standby kills, so Obtainium can keep
 * installing updates on its own.
 */
final class KeepAdbAlive {

    private static final String SHIZUKU = "moe.shizuku.privileged.api";
    private static final String COUNT_SERVER = "ps -A | grep -c shizuku_server";
    private static final String START_SERVER = "P=$(pm path " + SHIZUKU
            + " | sed 's/package://;s/base.apk//'); ${P}lib/arm/libshizuku.so";
    private static final long MIN_GAP_MS = 60_000;
    private static final int ATTEMPTS = 12;
    private static final long RETRY_MS = 5_000;
    private static volatile long lastRun = -MIN_GAP_MS;

    private KeepAdbAlive() { }

    static void kick(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRun < MIN_GAP_MS) return;

        lastRun = now;
        Context app = context.getApplicationContext();
        new Thread(() -> run(app), "keep-adb-alive").start();
    }

    private static void run(Context context) {
        enableAdb(context);
        if (!installed(context)) return;

        LocalAdb adb = new LocalAdb(context);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                if (!adb.shell(COUNT_SERVER).trim().equals("0")) return;
                adb.shell(START_SERVER);
                return;
            } catch (Exception notReady) {
                pause();
            }
        }
    }

    private static void enableAdb(Context context) {
        try {
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 1) {
                Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
            }
        } catch (RuntimeException noPermission) {
            // WRITE_SECURE_SETTINGS not granted: nothing to do.
        }
    }

    private static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU, 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_MS);
        } catch (InterruptedException stop) {
            Thread.currentThread().interrupt();
        }
    }
}
