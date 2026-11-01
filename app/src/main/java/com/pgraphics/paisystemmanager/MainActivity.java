package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

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

public class MainActivity extends Activity {

    private static final String TAG = "PAI_MAIN";

    private static final String API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int VOICE_REQUEST_CODE = 2001;

    private LinearLayout mainLayout;
    private LinearLayout chatLayout;

    private EditText messageInput;
    private TextView statusText;

    private TextToSpeech textToSpeech;

    private boolean ttsReady = false;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    // =========================================
    // ACTIVITY LIFECYCLE
    // =========================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();
        initializeTextToSpeech();
        requestRequiredPermissions();
    }

    // =========================================
    // USER INTERFACE
    // =========================================

    private void createInterface() {

        ScrollView scrollView = new ScrollView(this);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 24, 20, 24);
        mainLayout.setBackgroundColor(0xFF101820);

        scrollView.addView(mainLayout);

        TextView title = new TextView(this);
        title.setText("P AI SYSTEM MANAGER");
        title.setTextSize(25);
        title.setTextColor(0xFFFFD700);
        title.setGravity(Gravity.CENTER);
        title.setPadding(5, 20, 5, 15);

        mainLayout.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "Your personal AI assistant.\n" +
                "Ask questions and control your phone."
        );
        description.setTextSize(15);
        description.setTextColor(0xFFFFFFFF);
        description.setGravity(Gravity.CENTER);
        description.setPadding(5, 5, 5, 20);

        mainLayout.addView(description);

        statusText = new TextView(this);
        statusText.setText("Status: Ready");
        statusText.setTextSize(14);
        statusText.setTextColor(0xFF00FF99);
        statusText.setPadding(5, 10, 5, 15);

        mainLayout.addView(statusText);

        addButton(
                "START BACKGROUND LISTENING",
                v -> startBackgroundListening()
        );

        addButton(
                "STOP BACKGROUND LISTENING",
                v -> stopBackgroundListening()
        );

        TextView chatTitle = new TextView(this);
        chatTitle.setText("AI CHAT");
        chatTitle.setTextSize(20);
        chatTitle.setTextColor(0xFFFFD700);
        chatTitle.setPadding(5, 25, 5, 10);

        mainLayout.addView(chatTitle);

        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(5, 5, 5, 10);

        mainLayout.addView(chatLayout);

        messageInput = new EditText(this);
        messageInput.setHint("Ask P AI anything...");
        messageInput.setTextColor(0xFFFFFFFF);
        messageInput.setHintTextColor(0xFFAAAAAA);
        messageInput.setTextSize(16);
        messageInput.setPadding(15, 12, 15, 12);
        messageInput.setBackgroundColor(0xFF263746);

        mainLayout.addView(
                messageInput,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        addButton(
                "SEND MESSAGE",
                v -> sendMessage()
        );

        addButton(
                "SPEAK COMMAND",
                v -> startManualVoiceInput()
        );

        addButton(
                "STOP SPEAKING",
                v -> stopSpeaking()
        );

        setContentView(scrollView);
    }

    private void addButton(
            String text,
            View.OnClickListener listener
    ) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);

        button.setBackgroundTintList(
                ColorStateList.valueOf(0xFFB8860B)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 8, 0, 8);

        mainLayout.addView(button, params);

        button.setOnClickListener(listener);
    }

    private void addChatMessage(
            String sender,
            String message
    ) {

        TextView textView = new TextView(this);

        textView.setText(sender + ":\n" + message);
        textView.setTextSize(15);
        textView.setTextColor(0xFFFFFFFF);
        textView.setPadding(12, 12, 12, 12);
        textView.setBackgroundColor(0xFF263746);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 5, 0, 5);

        chatLayout.addView(textView, params);
    }

    // =========================================
    // TEXT TO SPEECH
    // =========================================

    private void initializeTextToSpeech() {

        textToSpeech = new TextToSpeech(
                this,
                status -> {

                    if (status == TextToSpeech.SUCCESS) {

                        int result = textToSpeech.setLanguage(
                                Locale.getDefault()
                        );

                        ttsReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;
                    }
                }
        );
    }

    private void speak(String message) {

        if (ttsReady && textToSpeech != null) {

            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAI_RESPONSE"
            );
        }
    }

    private void stopSpeaking() {

        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    // =========================================
    // PERMISSIONS
    // =========================================

    private void requestRequiredPermissions() {

        ArrayList<String> permissions = new ArrayList<>();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            permissions.add(
                    Manifest.permission.POST_NOTIFICATIONS
            );
        }

        if (!permissions.isEmpty()) {

            requestPermissions(
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST_CODE) {

            boolean microphoneGranted = false;

            for (int i = 0; i < permissions.length; i++) {

                if (Manifest.permission.RECORD_AUDIO.equals(
                        permissions[i]
                ) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {

                    microphoneGranted = true;
                }
            }

            if (microphoneGranted) {
                updateStatus("Microphone permission granted.");
            } else {
                updateStatus("Microphone permission is required.");
            }
        }
    }

    // =========================================
    // BACKGROUND VOICE SERVICE
    // =========================================

    private void startBackgroundListening() {

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(
                    this,
                    "Grant microphone permission first.",
                    Toast.LENGTH_LONG
            ).show();

            requestRequiredPermissions();
            return;
        }

        try {

            Intent intent = new Intent(
                    this,
                    VoiceRecognitionService.class
            );

            intent.setAction(
                    VoiceRecognitionService.ACTION_START
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            updateStatus("Starting background voice assistant.");

        } catch (Exception exception) {

            updateStatus("Unable to start voice assistant.");

            Toast.makeText(
                    this,
                    "Could not start voice service.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void stopBackgroundListening() {

        try {

            Intent intent = new Intent(
                    this,
                    VoiceRecognitionService.class
            );

            intent.setAction(
                    VoiceRecognitionService.ACTION_STOP
            );

            startService(intent);

            updateStatus("Stopping background voice assistant.");

        } catch (Exception exception) {

            updateStatus("Unable to stop voice assistant.");
        }
    }

    // =========================================
    // MANUAL VOICE INPUT
    // =========================================

    private void startManualVoiceInput() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            Toast.makeText(
                    this,
                    "Speech recognition is unavailable.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak your command."
        );

        try {

            startActivityForResult(
                    intent,
                    VOICE_REQUEST_CODE
            );

        } catch (Exception exception) {

            Toast.makeText(
                    this,
                    "Unable to open voice recognition.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

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

    // =========================================
    // PHONE COMMAND PROCESSING
    // =========================================

    private void processPhoneCommand(String command) {

        if (command == null) {
            return;
        }

        String cleanedCommand = command
                .toLowerCase(Locale.ROOT)
                .trim();

        if (cleanedCommand.startsWith("open ")) {

            String applicationName =
                    cleanedCommand.substring(5).trim();

            if (!applicationName.isEmpty()) {
                openApplication(applicationName);
            }

            return;
        }

        sendMessage();
    }

    private void openApplication(String applicationName) {

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

                        addChatMessage(
                                "P AI",
                                "I cannot open that application."
                        );

                        return;
                    }

                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    startActivity(launchIntent);

                    addChatMessage(
                            "P AI",
                            "Opening " + installedName + "."
                    );

                    speak("Opening " + installedName);

                    return;
                }
            }

            addChatMessage(
                    "P AI",
                    "I could not find " + applicationName + " on your phone."
            );

        } catch (Exception exception) {

            addChatMessage(
                    "P AI",
                    "Unable to open the application."
            );
        }
    }

    // =========================================
    // ONLINE AI CONNECTION
    // =========================================

    private void sendMessage() {

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

        messageInput.setText("");

        addChatMessage("You", question);

        updateStatus("P AI is thinking...");

        aiExecutor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(API_URL);

                connection = (HttpURLConnection) url.openConnection();

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

                try (OutputStream outputStream =
                             connection.getOutputStream()) {

                    outputStream.write(requestBytes);
                    outputStream.flush();
                }

                int responseCode = connection.getResponseCode();

                InputStream inputStream;

                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }

                if (inputStream == null) {
                    throw new Exception("The AI server returned no response.");
                }

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                inputStream,
                                StandardCharsets.UTF_8
                        )
                )) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception(
                            "AI server error: " + responseCode
                    );
                }

                JSONObject jsonResponse =
                        new JSONObject(response.toString());

                String reply = jsonResponse.optString(
                        "reply",
                        ""
                ).trim();

                if (reply.isEmpty()) {
                    throw new Exception("The AI returned an empty answer.");
                }

                runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    addChatMessage("P AI", reply);

                    updateStatus("Ready");

                    speak(reply);
                });

            } catch (Exception exception) {

                String errorMessage = exception.getMessage();

                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "Check you