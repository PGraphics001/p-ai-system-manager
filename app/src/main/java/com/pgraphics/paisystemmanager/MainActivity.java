package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MainActivity extends Activity {

    private static final String TAG = "PAI_SYSTEM_MANAGER";

    private static final String API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private static final String ACTION_START_VOICE =
            "com.pgraphics.paisystemmanager.START_VOICE";

    private static final String ACTION_STOP_VOICE =
            "com.pgraphics.paisystemmanager.STOP_VOICE";

    private static final int VOICE_REQUEST_CODE = 2001;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private LinearLayout chatLayout;
    private EditText messageInput;
    private TextView statusText;
    private ScrollView chatScrollView;
    private Button sendButton;

    private TextToSpeech textToSpeech;

    private boolean ttsReady = false;
    private boolean activityDestroyed = false;
    private boolean requestInProgress = false;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();
        initializeTextToSpeech();
        requestMicrophonePermission();
    }

    @Override
    protected void onDestroy() {

        activityDestroyed = true;

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        aiExecutor.shutdownNow();

        super.onDestroy();
    }

    // --------------------------------------------------
    // USER INTERFACE
    // --------------------------------------------------

    private void createInterface() {

        LinearLayout mainLayout = new LinearLayout(this);

        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        mainLayout.setBackgroundColor(0xFF101010);

        TextView title = new TextView(this);

        title.setText("P AI SYSTEM MANAGER");
        title.setTextSize(24);
        title.setTextColor(0xFFFFD700);
        title.setGravity(Gravity.CENTER);
        title.setPadding(10, 20, 10, 20);

        mainLayout.addView(title);

        statusText = new TextView(this);

        statusText.setText("P AI is ready.");
        statusText.setTextSize(14);
        statusText.setTextColor(0xFF00FF99);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(10, 10, 10, 15);

        mainLayout.addView(statusText);

        Button startListeningButton =
                createButton("START BACKGROUND LISTENING");

        startListeningButton.setOnClickListener(
                view -> startBackgroundListening()
        );

        mainLayout.addView(startListeningButton);

        Button stopListeningButton =
                createButton("STOP BACKGROUND LISTENING");

        stopListeningButton.setOnClickListener(
                view -> stopBackgroundListening()
        );

        mainLayout.addView(stopListeningButton);

        chatScrollView = new ScrollView(this);

        chatLayout = new LinearLayout(this);

        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(5, 10, 5, 10);

        chatScrollView.addView(chatLayout);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        scrollParams.setMargins(0, 15, 0, 15);

        mainLayout.addView(chatScrollView, scrollParams);

        messageInput = new EditText(this);

        messageInput.setHint("Type a message to P AI...");
        messageInput.setTextColor(0xFFFFFFFF);
        messageInput.setHintTextColor(0xFFAAAAAA);
        messageInput.setMinLines(2);
        messageInput.setMaxLines(5);
        messageInput.setPadding(15, 12, 15, 12);
        messageInput.setBackgroundColor(0xFF292929);

        mainLayout.addView(messageInput);

        sendButton = createButton("SEND MESSAGE");

        sendButton.setOnClickListener(
                view -> sendMessage()
        );

        mainLayout.addView(sendButton);

        Button speakButton =
                createButton("SPEAK COMMAND");

        speakButton.setOnClickListener(
                view -> startManualVoiceInput()
        );

        mainLayout.addView(speakButton);

        Button stopSpeechButton =
                createButton("STOP SPEAKING");

        stopSpeechButton.setOnClickListener(
                view -> stopSpeaking()
        );

        mainLayout.addView(stopSpeechButton);

        setContentView(mainLayout);
    }

    private Button createButton(String text) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(0xFF000000);

        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        0xFFFFD700
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 5, 0, 5);

        button.setLayoutParams(params);

        return button;
    }

    private void addChatMessage(String sender, String message) {

        if (activityDestroyed || chatLayout == null) {
            return;
        }

        TextView messageView = new TextView(this);

        messageView.setText(sender + ": " + message);
        messageView.setTextSize(15);
        messageView.setTextColor(0xFFFFFFFF);
        messageView.setPadding(12, 10, 12, 10);

        chatLayout.addView(messageView);

        chatScrollView.post(
                () -> chatScrollView.fullScroll(
                        ScrollView.FOCUS_DOWN
                )
        );
    }

    private void updateStatus(String message) {

        if (!activityDestroyed && statusText != null) {
            statusText.setText(message);
        }
    }

    // --------------------------------------------------
    // TEXT TO SPEECH
    // --------------------------------------------------

    private void initializeTextToSpeech() {

        textToSpeech = new TextToSpeech(
                getApplicationContext(),
                status -> {

                    if (activityDestroyed || textToSpeech == null) {
                        return;
                    }

                    if (status == TextToSpeech.SUCCESS) {

                        int result = textToSpeech.setLanguage(
                                Locale.getDefault()
                        );

                        ttsReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;

                        if (ttsReady) {
                            updateStatus("P AI is ready.");
                        }

                    } else {

                        ttsReady = false;
                    }
                }
        );
    }

    private void speak(String message) {

        if (ttsReady && textToSpeech != null
                && !activityDestroyed) {

            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAI_SPEECH"
            );
        }
    }

    private void stopSpeaking() {

        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    // --------------------------------------------------
    // PERMISSIONS
    // --------------------------------------------------

    private void requestMicrophonePermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.RECORD_AUDIO
                        },
                        PERMISSION_REQUEST_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST_CODE) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                updateStatus("Microphone permission granted.");

            } else {

                updateStatus(
                        "Microphone permission is required for voice features."
                );

                Toast.makeText(
                        this,
                        "Please allow microphone permission.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private boolean hasMicrophonePermission() {

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED;
    }

    // --------------------------------------------------
    // BACKGROUND VOICE SERVICE
    // --------------------------------------------------

    private void startBackgroundListening() {

        if (!hasMicrophonePermission()) {

            requestMicrophonePermission();
            return;
        }

        Intent intent = new Intent(
                this,
                VoiceRecognitionService.class
        );

        intent.setAction(ACTION_START_VOICE);

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(intent);

            } else {

                startService(intent);
            }

            updateStatus(
                    "Starting background voice recognition..."
            );

        } catch (Exception e) {

            updateStatus(
                    "Unable to start background voice recognition."
            );

            Toast.makeText(
                    this,
                    "Voice service could not start.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void stopBackgroundListening() {

        Intent intent = new Intent(
                this,
                VoiceRecognitionService.class
        );

        intent.setAction(ACTION_STOP_VOICE);

        try {

            startService(intent);

            updateStatus(
                    "Stopping background voice recognition..."
            );

        } catch (Exception e) {

            updateStatus(
                    "Unable to stop background voice recognition."
            );
        }
    }

    // --------------------------------------------------
    // MANUAL SPEECH RECOGNITION
    // --------------------------------------------------

    private void startManualVoiceInput() {

        if (!hasMicrophonePermission()) {

            requestMicrophonePermission();
            return;
        }

        try {

            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak to P AI"
            );

            startActivityForResult(
                    intent,
                    VOICE_REQUEST_CODE
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Voice recognition is unavailable.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == VOICE_REQUEST_CODE
                && resultCode == RESULT_OK
                && data != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                    );

            if (results != null && !results.isEmpty()) {

                String command = results.get(0);

                messageInput.setText(command);

                processPhoneCommand(command);
            }
        }
    }

    // --------------------------------------------------
    // PHONE COMMANDS
    // --------------------------------------------------

    private void processPhoneCommand(String command) {

        if (command == null || command.trim().isEmpty()) {
            return;
        }

        String cleanedCommand = command.trim();

        if (cleanedCommand.toLowerCase(Locale.ROOT)
                .startsWith("open ")) {

            String applicationName =
                    cleanedCommand.substring(5).trim();

            openApplication(applicationName);

        } else {

            sendMessage();
        }
    }

    private void openApplication(String applicationName) {

        if (applicationName == null
                || applicationName.trim().isEmpty()) {

            speak("Please specify the application name.");
            return;
        }

        PackageManager packageManager = getPackageManager();

        try {

            for (ApplicationInfo application :
                    packageManager.getInstalledApplications(0)) {

                String label = packageManager
                        .getApplicationLabel(application)
                        .toString();

                if (label.equalsIgnoreCase(
                        applicationName.trim()
                )) {

                    Intent launchIntent =
                            packageManager.getLaunchIntentForPackage(
                                    application.packageName
                            );

                    if (launchIntent != null) {

                        startActivity(launchIntent);

                        String response = "Opening " + label;

                        addChatMessage("P AI", response);

                        speak(response);

                        return;
                    }
                }
            }

            String response =
                    "I could not find " + applicationName
                            + " on your phone.";

            addChatMessage("P AI", response);

            speak(response);

        } catch (Exception e) {

            String response =
                    "I could not open that application.";

            addChatMessage("P AI", response);

            speak(response);
        }
    }

    // --------------------------------------------------
    // AI COMMUNICATION
    // --------------------------------------------------

    private void sendMessage() {

        if (requestInProgress) {
            return;
        }

        String question = messageInput.getText()
                .toString()
                .trim();

        if (question.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please enter a message.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        requestInProgress = true;

        messageInput.setText("");

        addChatMessage("You", question);

        updateStatus("P AI is thinking...");

        sendButton.setEnabled(false);

        try {

            aiExecutor.execute(() -> {

                HttpURLConnection connection = null;

                try {

                    URL url = new URL(API_URL);

                    connection = (HttpURLConnection)
                            url.openConnection();

                    connection.setRequestMethod("POST");

                    connection.setConnectTimeout(20000);

                    connection.setReadTimeout(30000);

                    connection.setDoOutput(true);

                    connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    );

                    connection.setRequestProperty(
                            "Accept",
                            "application/json"
                    );

                    JSONObject requestBody = new JSONObject();

                    requestBody.put("message", question);

                    byte[] requestBytes =
                            requestBody.toString().getBytes(
                                    StandardCharsets.UTF_8
                            );

                    connection.setFixedLengthStreamingMode(
                            requestBytes.length
                    );

                    try (OutputStream outputStream =
                                 connection.getOutputStream()) {

                        outputStream.write(requestBytes);

                        outputStream.flush();
                    }

                    int responseCode =
                            connection.getResponseCode();

                    InputStream inputStream;

                    if (responseCode >= 200
                            && responseCode < 300) {

                        inputStream = connection.getInputStream();

                    } else {

                        inputStream = connection.getErrorStream();
                    }

                    if (inputStream == null) {

                        throw new Exception(
                                "The server returned no response."
                        );
                    }

                    StringBuilder responseBuilder =
                            new StringBuilder();

                    try (BufferedReader reader =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 inputStream,
                                                 StandardCharsets.UTF_8
                                         )
                                 )) {

                        String line;

                        while ((line = reader.readLine()) != nu