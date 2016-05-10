package com.jhcompany.graduation;

import android.app.Application;
import android.content.Context;
import android.support.multidex.MultiDex;

import net.danlew.android.joda.JodaTimeAndroid;

public class GraduationApplication extends Application {
    private static GraduationApplication APPLICATION;
    private static Context CONTEXT;

    private static int retryConnectCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        APPLICATION = this;
        CONTEXT = this;
        JodaTimeAndroid.init(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static GraduationApplication getApplication() {
        return APPLICATION;
    }

    public static Context getContext() {
        return CONTEXT;
    }

    public static void increaseRetryCount() {
        retryConnectCount++;
    }

    public static void resetRetryCount() {
        retryConnectCount = 0;
    }

    public static boolean shouldChangeServer() {
        return retryConnectCount > ServerConstants.MAX_RETRY_COUNT;
    }
}
