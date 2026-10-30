package com.pgraphics.paisystemmanager;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public class PAIApplication extends Application {

    private static final String TAG = "PAI_APP_CHECK";

    @Override
    public void onCreate() {
        super.onCreate();

        initializeFirebase();
    }

    private void initializeFirebase() {

        try {
            // Initialize Firebase
            FirebaseApp firebaseApp =
                    FirebaseApp.initializeApp(this);

            if (firebaseApp == null) {
                throw new IllegalStateException(
                        "Firebase initialization returned null."
                );
            }

            // Initialize Firebase App Check
            FirebaseAppCheck appCheck =
                    FirebaseAppCheck.getInstance(firebaseApp);

            // Install debug provider
            appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
            );

            Log.i(TAG,
                    "Firebase and App Check initialized successfully.");

        } catch (Exception exception) {

            Log.e(TAG,
                    "Firebase initialization failed.",
                    exception);

            throw new IllegalStateException(
                    "P AI System Manager could not initialize Firebase.",
                    exception
            );
        }
    }
}