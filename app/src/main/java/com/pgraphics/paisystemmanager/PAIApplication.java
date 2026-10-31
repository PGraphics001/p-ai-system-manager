package com.pgraphics.paisystemmanager;

import android.app.Application;
import android.util.Log;

public class PAIApplication extends Application {

    private static final String TAG = "PAI_SYSTEM_MANAGER";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "P AI System Manager started successfully.");
    }
}