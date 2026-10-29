package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {

    private static final String TAG = "PAI_AI";
    private static final int VOICE_REQUEST_CODE = 100;

    private EditText commandInput;
    private TextView responseText;
    private Button executeButton;

    private TextToSpeech textToSpeech;
    private boolean speechReady = false;
    private boolean requestRunning = false;

    private GenerativeModelFutures aiModel;

    private final Executor mainExecutor =
            command -> runOnUiThread(command);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeSpeech();
        initializeAI();
        createInterface();
    }

    private void initializeAI() {
        try {
            GenerativeModel model =
                    FirebaseAI.getInstance(
                            GenerativeBackend.googleAI()
                    ).generativeModel("gemini-3.5-flash");

            aiModel = GenerativeModelFutures.from(model);

            Log.d(TAG, "AI model initialized.");

        } catch (Exception e) {
            aiModel = null;

            Log.e(TAG, "AI initialization failed.", e);
        }
    }

    private void initializeSpeech() {
        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = textToSpeech.setLanguage(
                                Locale.getDefault()
                        );

                        speechReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;

                        if (speechReady) {
                            textToSpeech.setSpeechRate(1.0f);
                        }
                    }
                }
        );
    }

    private void createInterface() {
        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(24, 32, 24, 24);
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);

        title.setText("P AI SYSTEM MANAGER");
        title.setTextColor(Color.rgb(255, 215, 0));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);

        layout.addView(title);

        TextView subtitle = new TextView(this);

        subtitle.setText("Your Intelligent Phone Assistant");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 8, 0, 20);

        layout.addView(subtitle);

        commandInput = new EditText(this);

        commandInput.setHint("Ask P AI anything...");
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.LTGRAY);
        commandInput.setMinLines(1);
        commandInput.setMaxLines(4);

        layout.addView(commandInput);

        executeButton = new Button(this);

        executeButton.setText("ASK AI / EXECUTE");

        layout.addView(executeButton);

        Button voiceButton = new Button(this);

        voiceButton.setText("SPEAK COMMAND");

        layout.addView(voiceButton);

        Button stopButton = new Button(this);

        stopButton.setText("STOP SPEAKING");

        layout.addView(stopButton);

        ScrollView scrollView = new ScrollView(this);

        responseText = new TextView(this);

        responseText.setText(
                "Welcome to P AI System Manager!\n\n"
                + "Ask a question or try:\n"
                + "• Open WhatsApp\n"
                + "• Open YouTube\n"
                + "• Open Settings\n"
                + "• Open Camera"
        );

        responseText.setTextColor(Color.WHITE);
        responseText.setTextSize(16);
        responseText.setPadding(0, 24, 0, 16);

        scrollView.addView(responseText);

        layout.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        executeButton.setOnClickListener(
                view -> executeCommand()
        );

        voiceButton.setOnClickListener(
                view -> startVoiceRecognition()
        );

        stopButton.setOnClickListener(view -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }

            responseText.setText("Speech stopped.");
        });

        setContentView(layout);
    }

    private void speakResponse(String message) {
        responseText.setText(message);

        if (speechReady && textToSpeech != null) {
            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAI_RESPONSE"
            );
        }
    }

    private void startVoiceRecognition() {
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
                "Speak your command or question"
        );

        try {
            startActivityForResult(
                    intent,
                    VOICE_REQUEST_CODE
            );

        } catch (Exception e) {
            Log.e(TAG, "Speech recognition failed.", e);

            speakResponse(
                    "Speech recognition is unavailable."
            );
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
                commandInput.setText(results.get(0));
                executeCommand();
            }
        }
    }

    private void executeCommand() {
        String originalCommand = commandInput.getText()
                .toString()
                .trim();

        String command = originalCommand.toLowerCase(
                Locale.ROOT
        );

        hideKeyboard();

        if (command.isEmpty()) {
            speakResponse("Please type or speak a command.");
            return;
        }

        if (command.equals("stop speaking")
                || command.equals("stop talking")) {

            if (textToSpeech != null) {
                textToSpeech.stop();
            }

            responseText.setText("Speech stopped.");
            return;
        }

        if (command.equals("open whatsapp")
                || command.equals("whatsapp")) {

            openApp("com.whatsapp", "WhatsApp");
            return;
        }

        if (command.equals("open youtube")
                || command.equals("youtube")) {

            openApp(
                    "com.google.android.youtube",
                    "YouTube"
            );
            return;
        }

        if (command.equals("open settings")
                || command.equals("settings")) {

            openSettings();
            return;
        }

        if (command.equals("open camera")
                || command.equals("camera")) {

            openCamera();
            return;
        }

        if (command.equals("hello")
                || command.equals("hi")
                || command.equals("hello pai")) {

            speakResponse(
                    "Hello! I am P AI System Manager. "
                    + "How can I help you today?"
            );

            return;
        }

        if (command.contains("your name")) {
            speakResponse(
                    "My name is P AI System Manager."
            );

            return;
        }

        askGemini(originalCommand);
    }

    private void askGemini(String question) {
        if (aiModel == null) {
            speakResponse(
                    "The AI model failed to initialize. "
                    + "Please check the Firebase setup."
            );

            return;
        }

        if (requestRunning) {
            Toast.makeText(
                    this,
                    "Please wait for the current answer.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        requestRunning = true;
        executeButton.setEnabled(false);

        responseText.setText("P AI is thinking...");

        Content prompt = new Content.Builder()
                .addText(
                        "You are P AI System Manager, a helpful "
                        + "Android assistant. Answer the user's "
                        + "question clearly, accurately, and "
                        + "usefully. If you are uncertain, say so. "
                        + "Never claim to perform phone actions "
                        + "unless this app actually supports them."
                        + "\n\nUser question: "
                        + question
                )
                .build();

        try {
            ListenableFuture<GenerateContentResponse> future =
                    aiModel.generateContent(prompt);

            Futures.addCallback(
                    future,
                    new FutureCallback<GenerateContentResponse>() {

                        @Override
                        public void onSuccess(
                                GenerateContentResponse result) {

                            requestRunning = false;
                            executeButton.setEnabled(true);

                            String answer = result.getText();

                            if (answer == null
                                    || answer.trim().isEmpty()) {

                                speakResponse(
                                        "The AI returned an empty "
                                        + "answer. Please try again."
                                );

                            } else {
                                speakResponse(answer.trim());
                            }
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            requestRunning = false;
                            executeButton.setEnabled(true);

                            Log.e(
                                    TAG,
                                    "Gemini request failed.",
                                    error
                            );

                            String details = getErrorDetails(error);

                            speakResponse(
                                    "AI request failed: " + details
                            );
                        }
                    },
                    mainExecutor
            );

        } catch (Exception e) {
            requestRunning = false;
            executeButton.setEnabled(true);

            Log.e(TAG, "Could not submit AI request.", e);

            speakResponse(
                    "Could not submit the AI request: "
                    + getErrorDetails(e)
            );
        }
    }

    private String getErrorDetails(Throwable error) {
        Throwable current = error;

        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (message == null || message.trim().isEmpty()) {
            message = current.getClass().getSimpleName();
        }

        if (message.length() > 300) {
            message = message.substring(0, 300);
        }

        return message;
    }

    private void openApp(
            String packageName,
            String appName) {

        try {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(packageName);

            if (intent != null) {
                speakResponse("Opening " + appName + ".");
                startActivity(intent);

            } else {
                speakResponse(
                        appName + " is not installed or unavailable."
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not open " + appName, e);

            speakResponse("Unable to open " + appName + ".");
        }
    }

    private void openSettings() {
        try {
            speakResponse("Opening Settings.");

            startActivity(
                    new Intent(Settings.ACTION_SETTINGS)
            );

        } catch (Exception e) {
            Log.e(TAG, "Could not open Settings.", e);

            speakResponse("Unable to open Settings.");
        }
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(
                    "android.media.action.IMAGE_CAPTURE"
            );

            if (intent.resolveActivity(
                    getPackageManager()) != null) {

                speakResponse("Opening Camera.");
                startActivity(intent);

            } else {
                speakResponse("No camera application was found.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not open Camera.", e);

            speakResponse("Unable to open Camera.");
        }
    }

    private void hideKeyboard() {
        if (commandInput == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    commandInput.getWindowToken(),
                    0
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}