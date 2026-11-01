package com.pgraphics.paisystemmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.util.Locale;

public class VoiceRecognitionService extends Service
        implements RecognitionListener {

    private static final String TAG = "PAI_VOICE";

    public static final String ACTION_START =
            "com.pgraphics.paisystemmanager.START_VOICE";

    public static final String ACTION_STOP =
            "com.pgraphics.paisystemmanager.STOP_VOICE";

    private static final String CHANNEL_ID = "pai_voice_assistant";
    private static final int NOTIFICATION_ID = 2001;

    private static final String WAKE_PHRASE = "hey p ai";

    private static final long RESTART_DELAY_MS = 500L;

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;

    private Handler handler;

    private boolean listening = false;
    private boolean destroyed = false;
    private boolean wakePhraseDetected = false;

    private final Runnable restartTask = () -> {
        if (!destroyed && !listening) {
            startRecognition();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();

        Log.i(TAG, "Voice service created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 34
                && checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Microphone permission not granted.");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startVoiceForeground();
            initializeVoiceEngine();
        } catch (Exception exception) {
            Log.e(TAG, "Unable to start voice service.", exception);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void startVoiceForeground() {

        Intent openIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification =
                new Notification.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setContentTitle("P AI System Manager")
                        .setContentText("Offline voice assistant is listening")
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "P AI Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Shows when P AI background voice recognition is active."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeVoiceEngine() {

        if (model != null || destroyed) {
            return;
        }

        File modelDirectory = new File(
                getFilesDir(),
                "model-en-us"
        );

        if (!modelDirectory.isDirectory()) {
            Log.e(TAG, "Vosk model directory is missing.");
            stopSelf();
            return;
        }

        try {
            model = new Model(modelDirectory.getAbsolutePath());

            recognizer = new Recognizer(model, 16000.0f);

            speechService = new SpeechService(
                    recognizer,
                    16000.0f
            );

            startRecognition();

            Log.i(TAG, "Offline voice engine initialized.");

        } catch (Exception exception) {
            Log.e(TAG, "Vosk initialization failed.", exception);
            stopSelf();
        }
    }

    private void startRecognition() {

        if (destroyed || speechService == null || listening) {
            return;
        }

        try {
            wakePhraseDetected = false;
            listening = true;

            speechService.startListening(this);

            Log.i(TAG, "Listening started.");

        } catch (Exception exception) {
            listening = false;

            Log.e(TAG, "Could not start recognition.", exception);

            scheduleRestart();
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {

        try {
            JSONObject result = new JSONObject(hypothesis);

            String partial = result.optString("partial", "")
                    .toLowerCase(Locale.ROOT)
                    .trim();

            if (partial.contains(WAKE_PHRASE)) {
                wakePhraseDetected = true;
            }

        } catch (Exception exception) {
            Log.e(TAG, "Partial result error.", exception);
        }
    }

    @Override
    public void onResult(String hypothesis) {
        // Intermediate recognition results are not executed.
    }

    @Override
    public void onFinalResult(String hypothesis) {

        try {
            JSONObject result = new JSONObject(hypothesis);

            String recognizedText = result.optString("text", "")
                    .toLowerCase(Locale.ROOT)
                    .trim();

            if (recognizedText.isEmpty()) {
                return;
            }

            boolean containsWakePhrase =
                    recognizedText.contains(WAKE_PHRASE);

            if (containsWakePhrase || wakePhraseDetected) {

                String command = recognizedText
                        .replace(WAKE_PHRASE, "")
                        .trim();

                wakePhraseDetected = false;

                if (!command.isEmpty()) {
                    executeCommand(command);
                }
            }

        } catch (Exception exception) {
            Log.e(TAG, "Final recognition error.", exception);

        } finally {
            listening = false;
            scheduleRestart();
        }
    }

    private void executeCommand(String command) {

        Log.i(TAG, "Recognized command: " + command);

        if (command.startsWith("open ")) {

            String applicationName = command
                    .substring(5)
                    .trim();

            launchApplication(applicationName);
        }
    }

    private void launchApplication(String applicationName) {

        PackageManager packageManager = getPackageManager();

        try {
            for (ApplicationInfo application :
                    packageManager.getInstalledApplications(
                            PackageManager.GET_META_DATA
                    )) {

                String installedName =
                        packageManager.getApplicationLabel(application)
                                .toString();

                if (installedName.equalsIgnoreCase(applicationName)) {

                    Intent launchIntent =
                            packageManager.getLaunchIntentForPackage(
                                    application.packageName
                            );

                    if (launchIntent != null) {

                        launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                        );

                        startActivity(launchIntent);

                        Log.i(TAG, "Opened " + installedName);

                        return;
                    }
                }
            }

            Log.w(TAG, "Application not found: " + applicationName);

        } catch (Exception exception) {
            Log.e(TAG, "Application launch failed.", exception);
        }
    }

    private void scheduleRestart() {

        if (destroyed || handler == null) {
            return;
        }

        handler.removeCallbacks(restartTask);

        handler.postDelayed(
                restartTask,
                RESTART_DELAY_MS
        );
    }

    @Override
    public void onError(Exception exception) {

        Log.e(TAG, "Speech recognition error.", exception);

        listening = false;
        scheduleRestart();
    }

    @Override
    public void onTimeout() {

        listening = false;
        scheduleRestart();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {

        destroyed = true;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }

        if (model != null) {
            model.close();
            model = null;
        }

        listening = false;

        Log.i(TAG, "Voice service stopped.");

        super.onDestroy();
    }
}