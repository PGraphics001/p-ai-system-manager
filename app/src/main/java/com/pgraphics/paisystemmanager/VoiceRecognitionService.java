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

    private static final long COMMAND_TIMEOUT_MS = 10000L;

    private static final String[] WAKE_PHRASES = {
            "hey p ai",
            "hey p i",
            "hey pay eye",
            "hey pay ai",
            "hey pie",
            "hey pa i",
            "hey p a i"
    };

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

    private final Runnable commandTimeoutTask = () -> {

        if (!destroyed && awaitingCommand) {

            awaitingCommand = false;

            updateNotification(
                    "Command timed out. Say Hey P AI again."
            );

            Log.i(TAG, "Command listening timed out.");
        }
    };

    // ================================================
    // SERVICE LIFECYCLE
    // ================================================

    @Override
    public void onCreate() {

        super.onCreate();

        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();

        Log.i(TAG, "P AI voice service created.");
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent != null
                && ACTION_STOP.equals(intent.getAction())) {

            stopSelf();

            return START_NOT_STICKY;
        }

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Microphone permission denied.");

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

            Log.e(
                    TAG,
                    "Service startup failed.",
                    exception
            );

            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    // ================================================
    // FOREGROUND NOTIFICATION
    // ================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "P AI Voice Assistant",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Background voice assistant status."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {

                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String message) {

        Intent openIntent =
                new Intent(this, MainActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder = new Notification.Builder(
                    this,
                    CHANNEL_ID
            );

        } else {

            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(
                        android.R.drawable.ic_btn_speak_now
                )
                .setContentTitle("P AI System Manager")
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void startVoiceForeground() {

        Notification notification =
                buildNotification(
                        "Preparing offline voice assistant..."
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    private void updateNotification(String message) {

        if (destroyed) {
            return;
        }

        NotificationManager manager =
                getSystemService(NotificationManager.class);

        if (manager != null) {

            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(message)
            );
        }
    }

    // ================================================
    // OFFLINE MODEL INITIALIZATION
    // ================================================

    private void installAndInitializeModel() {

        installerExecutor.execute(() -> {

            Model loadedModel = null;

            Recognizer loadedRecognizer = null;

            SpeechService loadedSpeechService = null;

            try {

                if (destroyed) {
                    return;
                }

                updateNotification(
                        "Checking offline speech model..."
                );

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
                            "Offline speech model is unavailable."
                    );
                }

                Log.i(
                        TAG,
                        "Model directory: "
                                + modelDirectory.getAbsolutePath()
                );

                updateNotification(
                        "Loading offline speech engine..."
                );

                loadedModel = new Model(
                        modelDirectory.getAbsolutePath()
                );

                loadedRecognizer = new Recognizer(
                        loadedModel,
                        16000.0f
                );

                loadedSpeechService = new SpeechService(
                        loadedRecognizer,
                        16000.0f
                );

                if (destroyed) {

                    closeResources(
                            loadedSpeechService,
                            loadedRecognizer,
                            loadedModel
                    );

                    return;
                }

                model = loadedModel;
                recognizer = loadedRecognizer;
                speechService = loadedSpeechService;

                loadedModel = null;
                loadedRecognizer = null;
                loadedSpeechService = null;

                handler.post(() -> {

                    if (!destroyed) {

                        startRecognition();
                    }
                });

                Log.i(
                        TAG,
                        "Offline speech engine initialized."
                );

            } catch (Exception exception) {

                Log.e(
                        TAG,
                        "Offline model initialization failed.",
                        exception
                );

                closeResources(
                        loadedSpeechService,
                        loadedRecognizer,
                        loadedModel
                );

                handler.post(() -> {

                    if (!destroyed) {

                        updateNotification(
                                "Voice setup failed. Please retry."
                        );

                        stopSelf();
                    }
                });
            }
        });
    }

    private void closeResources(
            SpeechService speech,
            Recognizer recognizerInstance,
            Model modelInstance
    ) {

        if (speech != null) {

            try {
                speech.shutdown();
            } catch (Exception exception) {
                Log.e(TAG, "Speech cleanup failed.", exception);
            }
        }

        if (recognizerInstance != null) {

            try {
                recognizerInstance.close();
            } catch (Exception exception) {
                Log.e(TAG, "Recognizer cleanup failed.", exception);
            }
        }

        if (modelInstance != null) {

            try {
                modelInstance.close();
            } catch (Exception exception) {
                Log.e(TAG, "Model cleanup failed.", exception);
            }
        }
    }

    // ================================================
    // CONTINUOUS SPEECH RECOGNITION
    // ================================================

    private void startRecognition() {

        if (destroyed
                || speechService == null
                || listening) {

            return;
        }

        try {

            boolean started =
                    speechService.startListening(this);

            if (!started) {

                Log.w(
                        TAG,
                        "Vosk recognition was already running."
                );

                updateNotification(
                        "Voice recognition is already active."
                );

                return;
            }

            listening = true;

            updateNotification(
                    "Listening offline. Say Hey P AI."
            );

            Log.i(
                    TAG,
                    "Continuous microphone recognition started."
            );

        } catch (Exception exception) {

            listening = false;

            Log.e(
                    TAG,
                    "Could not start microphone recognition.",
                    exception
            );

            updateNotification(
                    "Microphone recognition failed. Restart P AI."
            );
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {

        if (destroyed) {
            return;
        }

        if (hypothesis == null || hypothesis.trim().isEmpty()) {
            return;
        }

        Log.d(
                TAG,
                "Partial speech: " + hypothesis
        );
    }

    @Override
    public void onResult(String hypothesis) {

        if (destroyed) {
            return;
        }

        Log.i(
                TAG,
                "Vosk speech result: " + hypothesis
        );

        processHypothesis(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {

        if (destroyed) {
            return;
        }

        Log.i(
                TAG,
                "Vosk final result: " + hypothesis
        );

        processHypothesis(hypothesis);

        listening = false;

        updateNotification(
                "Recognition session ended. Restart P AI."
        );
    }

    private void processHypothesis(String hypothesis) {

        try {

            if (hypothesis == null || hypothesis.trim().isEmpty()) {
                return;
            }

            JSONObject result =
                    new JSONObject(hypothesis);

            String recognizedText =
                    normalizeSpeech(
                            result.optString("text", "")
                    );

            if (recognizedText.isEmpty()) {
                return;
            }

            Log.i(
                    TAG,
                    "Recognized speech: " + recognizedText
            );

            processRecognizedSpeech(recognizedText);

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Unable to process speech result.",
                    exception
            );
        }
    }

    private String normalizeSpeech(String speech) {

        if (speech == null) {
            return "";
        }

        return speech
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limitText(String text) {

        final int maximumLength = 75;

        if (text.length() <= maximumLength) {
            return text;
        }

        return text.substring(0, maximumLength) + "...";
    }

    // ================================================
    // WAKE WORD DETECTION
    // ================================================

    private void processRecognizedSpeech(String text) {

        if (destroyed || text == null || text.isEmpty()) {
            return;
        }

        updateNotification(
                "Heard: " + limitText(text)
        );

        if (awaitingCommand) {

            awaitingCommand = false;

            handler.removeCallbacks(
                    commandTimeoutTask
            );

            Log.i(
                    TAG,
                    "Command received: " + text
            );

            executeCommand(text);

            return;
        }

        String detectedPhrase =
                findWakePhrase(text);

        if (detectedPhrase == null) {

            Log.d(
                    TAG,
                    "Wake phrase not detected."
            );

            updateNotification(
                    "Listening offline. Say Hey P AI."
            );

            return;
        }

        Log.i(
                TAG,
                "Wake phrase detected: " + detectedPhrase
        );

        String command =
                extractCommand(
                        text,
                        detectedPhrase
                );

        if (command.isEmpty()) {

            awaitingCommand = true;

            updateNotification(
                    "P AI activated. Waiting for your command."
            );

            handler.removeCallbacks(
                    commandTimeoutTask
            );

            handler.postDelayed(
                    commandTimeoutTask,
                    COMMAND_TIMEOUT_MS
            );

        } else {

            updateNotification(
                    "Processing: " + limitText(command)
            );

            executeCommand(command);
        }
    }

    private String findWakePhrase(String text) {

        for (String phrase : WAKE_PHRASES) {

            if (text.contains(phrase)) {
                return phrase;
            }
        }

        return null;
    }

    private String extractCommand(
            String text,
            String wakePhrase
    ) {

        int position = text.indexOf(wakePhrase);

        if (position < 0) {
            return "";
        }

        int commandStart =
                position + wakePhrase.length();

        return text.substring(commandStart).trim();
    }

    // ================================================
    // VOICE COMMAND PROCESSING
    // ================================================

    private void executeCommand(String command) {

        if (destroyed || command == null) {
            return;
        }

        String cleanedCommand =
                normalizeSpeech(command);

        if (cleanedCommand.isEmpty()) {

            updateNotification(
                    "No command recognised. Try again."
            );

            return;
        }

        Log.i(
                TAG,
                "Executing command: " + cleanedCommand
        );

        if (cleanedCommand.startsWith("open ")) {

            String applicationName =
                    cleanedCommand.substring(5).trim();

            if (applicationName.isEmpty()) {

                updateNotification(
                        "Please specify an application."
                );

                return;
            }

            updateNotification(
                    "Opening " + applicationName + "..."
            );

            launchApplication(applicationName);

        } else {

            updateNotification(
                    "Command not yet supported: "
                            + limitText(cleanedCommand)
            );

            Log.w(
                    TAG,
                    "Unsupported command: " + cleanedCommand
            );
        }
    }

    // ================================================
    // APPLICATION MANAGEMENT
    // ================================================

    private void launchApplication(String applicationName) {

        PackageManager packageManager =
                getPackageManager();

        try {

            for (ApplicationInfo application :
                    packageManager.getInstalledApplications(
                            PackageManager.GET_META_DATA
                    )) {

                String installedName =
                        packageManager.getApplicationLabel(
                                application
                        ).toString();

                if (installedName.equalsIgnoreCase(
                        applicationName
                )) {

                    Intent launchIntent =
                            packageManager.getLaunchIntentForPackage(
                                    application.packageName
                            );

                    if (launchIntent == null) {

                        updateNotification(
                                "Cannot open " + installedName
                        );

                        return;
                    }

                    launchIntent.addFlags(
                            Inntent.FLAG_ACTIVITY_NEW_TASK
                    );

                    startActivity(launchIntent);

                    updateNotification(
                            "Opening " + installedName
                    );

                    Log.i(
                            TAG,
                            "Application opened: " + installedName
                    );

                    return;
                }
            }

            updateNotification(
                    applicationName + " was not found."
            );

            Log.w(
                    TAG,
                    "Application not found: " + applicationName
            );

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Application launch failed.",
                    exception
            );

            updateNotification(
                    "Unable to open " + applicationName
            );
        }
    }

    // ================================================
    // ERROR HANDLING
    // ================================================

    @Override
    public void onError(Exception exception) {

        listening = false;

        Log.e(
                TAG,
                "Vosk recognition error.",
                exception
        );

        updateNotification(
                "Recognition error. Restart background listening."
        );
    }

    @Override
    public void onTimeout() {

        listening = false;

        Log.w(
                TAG,
                "Recognition timeout."
        );

        updateNotification(
                "Recognition timed out. Restart background listening."
        );
    }

    // ================================================
    // SERVICE CLEANUP
    // ================================================

    @Override
    public void onDestroy() {

        destroyed = true;

        awaitingCommand = false;

        listening = false;

        if (handler != null) {

            handler.removeCallbacksAndMessages(null);
        }

        installerExecutor.shutdownNow();

        if (speechService != null) {

            try {

                speechService.stop();

                speechService.shutdown();

            } catch (Exception exception) {

                Log.e(
                        TAG,
                        "Speech service cleanup failed.",
                        exception
                );
            }

            speechService = null;
        }

        if (recognizer != null) {

            try {
                recognizer.close();
            } catch (Exception exception) {
                Log.e(TAG, "Recognizer cleanup failed.", exception);
            }

            recognizer = null;
        }

        if (model != null) {

            try {
                model.close();
            } catch (Exception exception) {
                Log.e(TAG, "Model cleanup failed.", exception);
            }

            model = null;
        }

        Log.i(
                TAG,
                "P AI voice service destroyed."
        );

        super.onDestroy();
    }
}