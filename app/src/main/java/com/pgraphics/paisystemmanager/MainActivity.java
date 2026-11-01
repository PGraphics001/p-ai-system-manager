package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
import java.util.concurrent.Executors;S.O) {

                startForegroundService(serviceIntent);

            } else {

                startService(serviceIntent);
            }

            speakResponse(
                    "P AI background voice assistant is starting."
            );

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Unable to start voice service.",
                    exception
            );

            speakResponse(
                    "Unable to start background voice recognition."
            );
        }
    }

    private void stopBackgroundListening() {

        try {

            Intent serviceIntent = new Intent(
                    this,
                    VoiceRecognitionService.class
            );

            boolean stopped = stopService(serviceIntent);

            speakResponse(
                    stopped
                            ? "P AI background listening stopped."
                            : "P AI background listening is not running."
            );

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Unable to stop voice service.",
                    exception
            );

            speakResponse("Unable to stop background listening.");
        }
    }

    // ================================================
    // MANUAL VOICE INPUT
    // ================================================

    private void startManualVoiceRecognition() {

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

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Manual voice recognition failed.",
                    exception
            );

            speakResponse(
                    "Speech recognition is unavailable."
            );
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

                commandInput.setText(results.get(0));

                executeCommand();
            }
        }
    }

    // ================================================
    // COMMAND PROCESSING
    // ================================================

    private void executeCommand() {

        if (activityDestroyed) {
            return;
        }

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

        askAI(originalCommand);
    }

    // ================================================
    // ONLINE AI CONNECTION
    // ================================================

    private void askAI(String question) {

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

        try {

            aiExecutor.execute(() -> {

                HttpURLConnection connection = null;

                try {

                    URL url = new URL(AI_API_URL);

                    connection = (HttpURLConnection)
                            url.openConnection();

                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(60000);
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

                    byte[] requestBytes = requestBody.toString()
                            .getBytes(StandardCharsets.UTF_8);

                    try (OutputStream output =
                                 connection.getOutputStream()) {

                        output.write(requestBytes);
                        output.flush();
                    }

                    int statusCode = connection.getResponseCode();

                    InputStream inputStream =
                            statusCode >= 200 && statusCode < 300
                                    ? connection.getInputStream()
                                    : connection.getErrorStream();

                    StringBuilder responseBuilder =
                            new StringBuilder();

                    if (inputStream != null) {

                        try (BufferedReader reader =
                                     new BufferedReader(
                                             new InputStreamReader(
                                                     inputStream,
                                                     StandardCharsets.UTF_8
                                             )
                                     )) {

                            String line;

                            while ((line = reader.readLine()) != null) {
                                responseBuilder.append(line);
                            }
                        }
                    }

                    if (statusCode < 200 || statusCode >= 300) {

                        throw new Exception(
                                "Server returned HTTP " + statusCode
                        );
                    }

                    JSONObject responseJson =
                            new JSONObject(responseBuilder.toString());

                    String answer = responseJson.optString(
                            "reply",
                            ""
                    ).trim();

                    if (answer.isEmpty()) {

                        throw new Exception(
                                "The AI returned an empty response."
                        );
                    }

                    final String finalAnswer = answer;

                    runOnUiThread(() -> {

                        if (activityDestroyed || isFinishing()) {
                            return;
                        }

                        requestRunning = false;
                        executeButton.setEnabled(true);

                        speakResponse(finalAnswer);
                    });

                } catch (Exception exception) {

                    Log.e(
                            TAG,
                            "AI request failed.",
                            exception
                    );

                    String errorMessage =
                            "AI request failed: "
                            + exception.getMessage();

                    runOnUiThread(() -> {

                        if (activityDestroyed || isFinishing()) {
                            return;
                        }

                        
