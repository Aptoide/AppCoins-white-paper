package com.aptoide.proofofattention;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import java.util.List;
import java.util.concurrent.TimeUnit;
import rx.Observable;
import rx.Subscription;

public class ForegroundDetectionService extends Service {

  private ActivityManager activityManager;
  private PackageManager packageManager;
  private Subscription subscription;
  private PowerManager powerManager;
  private KeyguardManager keyguardManager;
  private DisplayManager displayManager;

  @Nullable @Override public IBinder onBind(Intent intent) {
    return null;
  }

  @Override public void onCreate() {
    super.onCreate();
    activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    packageManager = getPackageManager();
    powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
    }

    subscription = Observable.interval(5, TimeUnit.SECONDS)
        .doOnNext(__ -> logForegroundPackages())
        .subscribe();
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }

  @Override public void onDestroy() {
    if (subscription != null && !subscription.isUnsubscribed()) {
      subscription.unsubscribe();
    }
    super.onDestroy();
  }

  private void logForegroundPackages() {

    boolean screenOn;
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
      screenOn = powerManager.isInteractive();
    } else {
      screenOn = powerManager.isScreenOn();
    }

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
      screenOn = screenOn && (displayManager.getDisplays()[0].getState() == Display.STATE_ON);
    }

    Log.d("ProofOfAttention", "Screen ON: " + screenOn);

    boolean deviceLocked = false;
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
      deviceLocked = keyguardManager.isKeyguardLocked();
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      deviceLocked = keyguardManager.isDeviceLocked();
    }

    Log.d("ProofOfAttention", "Device Locked: " + deviceLocked);

    if (screenOn && !deviceLocked) {

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
        final List<ActivityManager.RunningAppProcessInfo> processes =
            activityManager.getRunningAppProcesses();

        boolean foreground = false;
        String[] packages;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
          packages = packageManager.getPackagesForUid(process.uid);
          if (packages != null && packages.length > 0) {
            for (int i = 0; i < packages.length; i++) {
              Log.d("ProofOfAttention",
                  "Running Package: " + packages[i] + " importance: " + process.importance);
              if (process.importance
                  == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                foreground = true;
                Log.d("ProofOfAttention", "Foreground Package: " + packages[i]);
              }
            }
          }
        }

        if (!foreground) {
          Log.d("ProofOfAttention", "Foreground Package: None");
        }
      } else {
        Log.d("ProofOfAttention", "Foreground Package: Unavailable");
      }

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        final List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);

        if (!tasks.isEmpty()) {
          final ComponentName componentInfo = tasks.get(0).topActivity;
          Log.d("ProofOfAttention", "Recent Task: " + componentInfo.getPackageName());
        } else {
          Log.d("ProofOfAttention", "Recent Task: None");
        }
      } else {
        Log.d("ProofOfAttention", "Recent Task: Unavailable");
      }
    }
  }
}
