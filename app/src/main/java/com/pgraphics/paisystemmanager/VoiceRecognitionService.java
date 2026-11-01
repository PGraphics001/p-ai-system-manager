package com.pgraphics.paisystemmanager;

import android.Manifest;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceRecognitionService extends Service
        implements RecognitionListener {

    private static final String TAG = "PAI_VOICE";

    public static final String ACTION_START =
            "com.pgraphics.paisystemmanager.START_VOICE";

    public static final String ACTION_STOP =
            "com.pgraphics.paisystemmanager.STOP_VOICE";

    private static final String CHANNEL_ID =
            "pai_voice_assistant";

    private static final int NOTIFICATION_ID = 2001;

    private static final String WAKE_PHRASE = "hey p ai";

    private static final long RESTART_DELAY_MS = 500L;

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;

    private Handler handler;

    private final ExecutorService installerExecutor =
            Executors.newSingleThreadExecutor();

    private volatile boolean destroyed = false;
    private volatile boolean listening = false;
    private volatile boolean awaitingCommand = false;
    private volatile boolean initializationStarted = false;

    private final Runnable restartTask = () -> {
        if (!destroyed && !listening && speechService != null) {
            startRecognition();
        }
    };

    // --------------------------------------------------
    // SERVICE LIFECYCLE
    // --------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();

        Log.i(TAG, "P AI voice service created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Microphone permission has not been granted.");

            stopSelf();

            return START_NOT_STICKY;
        }

        try {

            startVoiceForeground();

            if (!initializationStarted) {
                initializationStarted = true;
                installAndInitializeModel();
            }

        } catch (Exception exception) {

            Log.e(TAG, "Unable to start voice service.", exception);

            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --------------------------------------------------
    // FOREGROUND NOTIFICATION
    // --------------------------------------------------

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "P AI Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Displays the status of P AI background voice recognition."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
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

        Notification notification = buildNotification(
                "Preparing offline voice assistant...",
                pendingIntent
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );

        } else {

            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(
            String message,
            PendingIntent pendingIntent
    ) {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("P AI System Manager")
                .setContentText(message)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE);

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        return builder.build();
    }

    private void updateNotification(String message) {

        if (destroyed) {
            return;
        }

        Intent openIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = buildNotification(
                message,
                pendingIntent
        );

        NotificationManager manager =
                getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    // --------------------------------------------------
    // OFFLINE MODEL INSTALLATION
    // --------------------------------------------------

    private void installAndInitializeModel() {

        installerExecutor.execute(() -> {

            try {

                if (destroyed) {
                    return;
                }

                updateNotification("Checking offline speech model...");

                File modelDirectory =
                        OfflineModelInstaller.install(
                                getApplicationContext()
                        );

                if (destroyed) {
                    return;
                }

                if (modelDirectory == null
                        || !modelDirectory.isDirectory()) {

                    throw new IllegalStateException(
                            "Offline model directory is unavailable."
                    );
                }

                Log.i(
                        TAG,
                        "Model directory: "
                                + modelDirectory.getAbsolutePath()
                );

                updateNotification("Loading offline speech engine...");

                Model loadedModel = new Model(
                        modelDirectory.getAbsolutePath()
                );

                if (destroyed) {
                    loadedModel.close();
                    return;
                }

                Recognizer loadedRecognizer =
                        new Recognizer(loadedModel, 16000.0f);

                if (destroyed) {
                    loadedRecognizer.close();
                    loadedModel.close();
                    return;
                }

                SpeechService loadedSpeechService =
                        new SpeechService(
                                loadedRecognizer,
                                16000.0f
                        );

                if (destroyed) {
                    loadedSpeechService.shutdown();
                    loadedRecognizer.close();
                    loadedModel.close();
                    return;
                }

                model = loadedModel;
                recognizer = loadedRecognizer;
                speechService = loadedSpeechService;

                handler.post(() -> {

                    if (!destroyed) {

                        updateNotification(
                                "Offline voice assistant is listening."
                        );

                        startRecognition();
                    }
                });

                Log.i(TAG, "Offline speech engine initialized.");

            } catch (Exception exception) {

                Log.e(
                        TAG,
                        "Offline model installation or initialization failed.",
                        exception
                );

                handler.post(() -> {

                    if (!destroyed) {

                        updateNotification(
                                "Voice setup failed. Open P AI to retry."
                        );

                        stopSelf();
                    }
                });
            }
        });
    }

    // --------------------------------------------------
    // SPEECH RECOGNITION
    // --------------------------------------------------

    private void startRecognition() {

        if (destroyed || speechService == null || listening) {
            return;
        }

        try {

            listening = true;

            speechService.startListening(this);

            Log.i(TAG, "Listening for wake phrase.");

        } catch (Exception exception) {

            listening = false;

            Log.e(TAG, "Unable to start recognition.", exception);

            scheduleRestart();
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {

        // Partial results are deliberately not executed.
        // Commands are processed only after final recognition.
    }

    @Override
    public void onResult(String hypothesis) {

        // Intermediate results are deliberately ignored.
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

            Log.i(TAG, "Recognized speech: " + recognizedText);

            if (awaitingCommand) {

                awaitingCommand = false;

                executeCommand(recognizedText);

            } else if (recognizedText.contains(WAKE_PHRASE)) {

                String command = recognizedText
                        .replace(WAKE_PHRASE, "")
                        .trim();

                if (command.isEmpty()) {

                    awaitingCommand = true;

                    Log.i(
                            TAG,
                            "Wake phrase detected. Waiting for command."
                    );

                } else {

                    executeCommand(command);
                }
            }

        } catch (Exception exception) {

            Log.e(TAG, "Unable to process speech result.", exception);

        } finally {

            listening = false;

            scheduleRestart();
        }
    }

    // --------------------------------------------------
    // COMMAND PROCESSING
    // --------------------------------------------------

    private void executeCommand(String command) {

        if (destroyed || command == null) {
            return;
        }

        String cleanedCommand = command
                .toLowerCase(Locale.ROOT)
                .trim();

        Log.i(TAG, "Processing command: " + cleanedCommand);

        if (cleanedCommand.startsWith("open ")) {

            String applicationName = cleanedCommand
                    .substring(5)
                    .trim();

            if (!applicationName.isEmpty()) {
                launchApplication(applicationName);
            }

        } else {

            Log.i(
                    TAG,
                    "No supported offline command matched: "
                            + cleanedCommand
            );
        }
    }

    // --------------------------------------------------
    // APPLICATION LAUNCHING
    // --------------------------------------------------

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

                    if (launchIntent == null) {

                        Log.w(
                                TAG,
                                "No launch activity for: " + installedName
                        );

                        return;
                    }

                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    startActivity(launchIntent);

                    Log.i(TAG, "Successfully opened " + installedName);

                    return;
                }
            }

            Log.w(
                    TAG,
                    "Installed application not found: " + applicationName
            );

        } catch (Exception exception) {

            Log.e(TAG, "Application launch failed.", exception);
        }
    }

    // --------------------------------------------------
    // RECOGNITION RESTART
    // --------------------------------------------------

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

        Log.i(TAG, "Speech recognition timed out.");

        listening = false;

        scheduleRestart();
    }

    // --------------------------------------------------
    // RESOURCE CLEANUP
    // --------------------------------------------------

    @Override
    public void onDestroy() {

        destroyed = true;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        installerExecutor.shutdownNow();

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

        Log.i(TAG, "P AI voice service destroyed.");

        super.onDestroy();
    }
}