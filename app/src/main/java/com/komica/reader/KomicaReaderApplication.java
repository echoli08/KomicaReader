package com.komica.reader;

import android.app.Application;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

public class KomicaReaderApplication extends Application {
    private static boolean isAppInForeground = false;

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            public void onAppForeground() {
                isAppInForeground = true;
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            public void onAppInBackground() {
                isAppInForeground = false;
            }
        });
    }

    public static boolean isAppInForeground() {
        return isAppInForeground;
    }
}
