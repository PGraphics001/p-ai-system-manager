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

    private static final long RESTART_DELAY_MS = 500L;

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

    private final Runnable restartTask = () -> {

        if (!destroyed && !listening && speechService != null) {
            startRecognition();
        }
    };

    private final Runnable commandTimeoutTask = () -> {

        if (!destroyed && awaitingCommand) {

            awaitingCommand = false;

            updateNotification(
                    "Command timed out. Say Hey P AI to try again."
            );

            Log.i(TAG, "Voice command timed out.");
        }
    };

    // ==================================================
    // SERVICE LIFECYCLE
    // ==================================================

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

            Log.e(TAG, "Microphone permission not granted.");

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

            Log.e(TAG, "Voice service startup failed.", exception);

            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    // ==================================================
    // FOREGROUND NOTIFICATION
    // ==================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "P AI Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Background voice recognition status."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {

                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String message) {

        Intent openIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder = new Notification.Builder(this, CHANNEL_ID);

        } else {

            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("P AI System Manager")
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void startVoiceForeground() {

        Notification notification = buildNotification(
                "Preparing offline voice assistant..."
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

    // ==================================================
    // OFFLINE SPEECH MODEL INITIALIZATION
    // ==================================================

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

                    loadedSpeechService.shutdown();
                    loadedRecognizer.close();
                    loadedModel.close();

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

                        updateNotification(
                                "Offline voice assistant is listening."
                        );

                        startRecognition();
                    }
                });

                Log.i(
                        TAG,
                        "Offline speech engine initialized successfully."
                );

            } catch (Exception exception) {

                Log.e(
                        TAG,
                        "Offline speech initialization failed.",
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
                                "Voice setup failed. Open P AI to retry."
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

            speech.shutdown();
        }

        if (recognizerInstance != null) {

            recognizerInstance.close();
        }

        if (modelInstance != null) {

            modelInstance.close();
        }
    }

    // ==================================================
    // SPEECH RECOGNITION ENGINE
    // ==================================================

    private void startRecognition() {

        if (destroyed || speechService == null || listening) {
            return;
        }

        try {

            listening = true;

            speechService.startListening(this);

            Log.i(TAG, "Listening for Hey P AI.");

        } catch (Exception exception) {

            listening = false;

            Log.e(
                    TAG,
                    "Unable to start speech recognition.",
                    exception
            );

            scheduleRestart();
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {

        // Partial recognition is intentionally not executed.
    }

    @Override
    public void onResult(String hypothesis) {

        // Intermediate recognition is intentionally ignored.
    }

    @Override
    public void onFinalResult(String hypothesis) {

        try {

            JSONObject result = new JSONObject(hypothesis);

            String recognizedText = normalizeSpeech(
                    result.optString("text", "")
            );

            if (recognizedText.isEmpty()) {
                return;
            }

            Log.i(
                    TAG,
                    "Vosk recognized: " + recognizedText
            );

            updateNotification(
                    "Heard: " + limitText(recognizedText)
            );

            processRecognizedSpeech(recognizedText);

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Speech result processing failed.",
                    exception
            );

        } finally {

            listening = false;

            scheduleRestart();
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

    // ==================================================
    // WAKE PHRASE DETECTION
    // ==================================================

    private void processRecognizedSpeech(String text) {

        if (destroyed) {
            return;
        }

        if (awaitingCommand) {

            awaitingCommand = false;

            handler.removeCallbacks(commandTimeoutTask);

            updateNotification(
                    "Processing: " + limitText(text)
            );

            executeCommand(text);

            return;
        }

        String detectedPhrase = findWakePhrase(text);

        if (detectedPhrase == null) {

            Log.d(
                    TAG,
                    "Wake phrase not detected: " + text
            );

            return;
        }

        Log.i(
                TAG,
                "Wake phrase detected: " + detectedPhrase
        );

        String command = extractCommand(
                text,
                detectedPhrase
        );

        if (command.isEmpty()) {

            awaitingCommand = true;

            updateNotification(
                    "P AI activated. Waiting for your command."
            );

            handler.removeCallbacks(commandTimeoutTask);

            handler.postDelayed(
                    commandTimeoutTask,
                    COMMAND_TIMEOUT_MS
            );

        } else {

            updateNotification(
                    "Wake phrase detected. Processing command."
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

        int commandStart = position + wakePhrase.length();

        return text.substring(commandStart).trim();
    }

    // ==================================================
    // VOICE COMMAND PROCESSING
    // ==================================================

    private void executeCommand(String command) {

        if (destroyed || command == null) {
            return;
        }

        String cleanedCommand = normalizeSpeech(command);

        if (cleanedCommand.isEmpty()) {

            updateNotification(
                    "No command recognised. Please try again."
            );

            return;
        }

        Log.i(
                TAG,
                "Executing voice command: " + cleanedCommand
        );

        if (cleanedCommand.startsWith("open ")) {

            String applicationName = cleanedCommand
                    .substring(5)
                    .trim();

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
                    "Unsupported command: "
                            + limitText(cleanedCommand)
            );

            Log.w(
                    TAG,
                    "Unsupported command: " + cleanedCommand
            );
        }
    }

    // ==================================================
    // INSTALLED APPLICATION MANAGEMENT
    // ==================================================

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
                                "No launch activity found for "
                                        + installedName
                        );

                        updateNotification(
                                "Cannot open " + installedName
                        );

                        return;
                    }

                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    try {

                        startActivity(launchIntent);

                        updateNotification(
                                "Opening " + installedName
                        );

                        Log.i(
                                TAG,
                                "Application launched: " + installedName
                        );

                    } catch (Exception exception) {

                        Log.e(
                                TAG,
                                "Application launch restricted.",
                                exception
                        );

                        updateNotification(
                                "Unable to open " + installedName
                        );
                    }

                    return;
                }
            }

            Log.w(
                    TAG,
                    "Application not found: " + applicationName
            );

            updateNotification(
                    applicationName + " was not found."
            );

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Application search failed.",
                    exception
            );

            updateNotification(
                    "Unable to search installed applications."
            );
        }
    }

    // ==================================================
    // RECOGNITION RESTART
    // ==================================================

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

        Log.e(
                TAG,
                "Speech recognition error.",
                exception
        );

      listening = false;

        scheduleRestart();
    }

    @Override
    public void onTimeout() {

        Log.d(
                TAG,
                "Speech recognition timeout."
        );

        listening = false;

        scheduleRestart();
    }

    // ==================================================
    // SERVICE CLEANUP
    // ==================================================

    @Override
    public void onDestroy() {

        destroyed = true;

        awaitingCommand = false;

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

        Log.i(
                TAG,
                "P AI voice service stopped."
        );

        super.onDestroy();
    }
}