package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private EditText commandInput;
    private TextView response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 40, 24, 24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);
        title.setText("🤖 P AI SYSTEM MANAGER");
        title.setTextColor(Color.rgb(255, 215, 0));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your Intelligent Phone Assistant");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        layout.addView(subtitle);

        commandInput = new EditText(this);
        commandInput.setHint("Type a command...");
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.LTGRAY);
        commandInput.setSingleLine(true);
        layout.addView(commandInput);

        Button execute = new Button(this);
        execute.setText("EXECUTE COMMAND");
        layout.addView(execute);

        response = new TextView(this);
        response.setText("System ready. Enter a command.");
        response.setTextColor(Color.WHITE);
        response.setTextSize(16);
        response.setPadding(0, 30, 0, 0);
        layout.addView(response);

        execute.setOnClickListener(v -> runCommand());

        setContentView(layout);
    }

    private void runCommand() {
        String command = commandInput.getText()
                .toString().trim().toLowerCase();

        ((InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(
                        commandInput.getWindowToken(), 0);

        if (command.isEmpty()) {
            response.setText("Please enter a command.");
            return;
        }

        if (command.contains("lock")) {
            response.setText(
                "Screen locking is not implemented yet. " +
                "We will add an authorized Android mechanism.");
        } else if (command.contains("whatsapp")) {
            openApp("com.whatsapp", "WhatsApp");
        } else if (command.contains("youtube")) {
            openApp("com.google.android.youtube", "YouTube");
        } else if (command.contains("settings")) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                response.setText("Opening Settings...");
            } catch (Exception e) {
                response.setText("Unable to open Settings.");
            }
        } else if (command.contains("camera")) {
            try {
                Intent intent = new Intent(
                    "android.media.action.IMAGE_CAPTURE");
                startActivity(intent);
                response.setText("Opening Camera...");
            } catch (Exception e) {
                response.setText("Unable to open Camera.");
            }
        } else {
            response.setText(
                "Command not recognized. Try opening WhatsApp, " +
                "YouTube, Settings, or Camera.");
        }
    }

    private void openApp(String packageName, String appName) {
        try {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(packageName);

            if (intent != null) {
                startActivity(intent);
                response.setText("Opening " + appName + "...");
            } else {
                response.setText(
                    appName + " is unavailable or not installed.");
            }
        } catch (Exception e) {
            response.setText("Unable to open " + appName + ".");
        }
    }
}