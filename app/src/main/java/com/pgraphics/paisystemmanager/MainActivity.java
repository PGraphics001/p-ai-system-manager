package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * P AI SYSTEM MANAGER
 *
 * Main chat screen.
 *
 * Current responsibilities:
 * - Online AI chat through the P AI Vercel API.
 * - Manual online speech input through Android's speech interface.
 * - User-controlled text-to-speech for individual AI messages.
 * - Local launching of installed applications.
 *
 * Offline recognition and the Vosk background service are deliberately
 * not started from this screen while that feature is being stabilised.
 */
public class MainActivity extends Activity {

    // ---------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------

    private static final String API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private static final int AUDIO_PERMISSION_REQUEST = 1001;
    private static final int SPEECH_REQUEST = 1002;

    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_TTS_CHUNK_LENGTH = 3000;

    private static final String HISTORY_PREFS = "pai_conversation_history";
    private static final String HISTORY_KEY = "messages";
    private static final String CONVERSATIONS_PREFS = "pai_conversations";
    private static final String CONVERSATIONS_KEY = "sessions";
    private static final String ACTIVE_CONVERSATION_KEY = "active_id";
    private static final int MAX_CONVERSATIONS = 100;
    private static final int MAX_SAVED_MESSAGES = 500;
    private static final int MAX_CONTEXT_HISTORY_MESSAGES = 20;
    private static final int MAX_CONTEXT_ITEM_LENGTH = 4000;
    private static final int MAX_CONTEXT_TOTAL_LENGTH = 16000;

    private static final int BACKGROUND_COLOR =
            Color.rgb(15, 15, 20);

    private static final int ACCENT_COLOR =
            Color.rgb(230, 180, 70);

    // ---------------------------------------------------------------------
    // User interface
    // ---------------------------------------------------------------------

    private LinearLayout rootLayout;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText messageInput;
    private TextView statusText;
    private Button sendButton;
    private Button voiceButton;
    private Button newChatButton;
    private Button chatHistoryButton;

    // ---------------------------------------------------------------------
    // System components
    // ---------------------------------------------------------------------

    private TextToSpeech textToSpeech;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    // ---------------------------------------------------------------------
    // Application state
    // ---------------------------------------------------------------------

    private volatile boolean isActivityDestroyed = false;
    private boolean requestInProgress = false;
    private boolean textToSpeechReady = false;

    private SharedPreferences historyPreferences;
    private SharedPreferences conversationsPreferences;
    private JSONArray conversations;
    private JSONArray conversationHistory;
    private String activeConversationId;

    private Button activeSpeechButton;
    private String activeSpeechRequestId;
    private String activeSpeechFinalUtteranceId;

    // ---------------------------------------------------------------------
    // Activity lifecycle
    // ---------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        historyPreferences = getSharedPreferences(
                HISTORY_PREFS,
                MODE_PRIVATE
        );
        conversationsPreferences = getSharedPreferences(
                CONVERSATIONS_PREFS,
                MODE_PRIVATE
        );
        loadConversations();

        buildInterface();
        initializeTextToSpeech();
        updateStatus("Ready");
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed = true;

        stopCurrentSpeech();

        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception ignored) {
                // The engine may already have been released.
            }
            textToSpeech = null;
        }

        aiExecutor.shutdownNow();

        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Interface construction
    // ---------------------------------------------------------------------

    private void buildInterface() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(10)
        );
        rootLayout.setBackgroundColor(BACKGROUND_COLOR);

        TextView title = new TextView(this);
        title.setText("P AI SYSTEM MANAGER");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(ACCENT_COLOR);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(8));

        rootLayout.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        statusText = new TextView(this);
        statusText.setText("Starting P AI...");
        statusText.setTextSize(13);
        statusText.setTextColor(Color.LTGRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(3), 0, dp(10));

        rootLayout.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout conversationToolbar = new LinearLayout(this);
        conversationToolbar.setOrientation(LinearLayout.HORIZONTAL);

        newChatButton = createButton("+ NEW CHAT");
        chatHistoryButton = createButton("CHAT HISTORY");
        newChatButton.setTextSize(11);
        chatHistoryButton.setTextSize(11);
        newChatButton.setTextColor(Color.WHITE);
        chatHistoryButton.setTextColor(Color.WHITE);
        newChatButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.rgb(65, 65, 75))
        );
        chatHistoryButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.rgb(65, 65, 75))
        );

        LinearLayout.LayoutParams newChatParams =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        newChatParams.setMargins(dp(3), dp(2), dp(3), dp(5));
        LinearLayout.LayoutParams historyParams =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        historyParams.setMargins(dp(3), dp(2), dp(3), dp(5));
        conversationToolbar.addView(newChatButton, newChatParams);
        conversationToolbar.addView(chatHistoryButton, historyParams);
        rootLayout.addView(conversationToolbar);

        newChatButton.setOnClickListener(v -> createNewConversation());
        chatHistoryButton.setOnClickListener(v -> showConversationHistory());

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setSmoothScrollingEnabled(true);

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(
                dp(3),
                dp(4),
                dp(3),
                dp(8)
        );

        chatScroll.addView(
                chatContainer,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        rootLayout.addView(
                chatScroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        messageInput = new EditText(this);
        messageInput.setHint("Ask P AI anything...");
        messageInput.setTextColor(Color.WHITE);
        messageInput.setHintTextColor(Color.GRAY);
        messageInput.setTextSize(16);
        messageInput.setSingleLine(false);
        messageInput.setMinLines(2);
        messageInput.setMaxLines(5);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        messageInput.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        rootLayout.addView(
                messageInput,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        sendButton = createButton("SEND MESSAGE");
        voiceButton = createButton("VOICE INPUT");

        rootLayout.addView(sendButton);
        rootLayout.addView(voiceButton);

        sendButton.setOnClickListener(v -> sendMessage());
        voiceButton.setOnClickListener(v -> startManualVoiceRecognition());

        setContentView(rootLayout);

        restoreConversationHistory();
    }

    private Button createButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.BLACK);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(ACCENT_COLOR)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, dp(4), 0, dp(4));
        button.setLayoutParams(params);

        return button;
    }

    // ---------------------------------------------------------------------
    // Chat message rendering
    // ---------------------------------------------------------------------

    /**
     * Adds a message to the conversation.
     * Only messages from P AI receive an individual read-aloud button.
     */
    private void addChatMessage(String sender, String message) {
        if (isActivityDestroyed || chatContainer == null) {
            return;
        }

        String safeSender = sender == null ? "System" : sender;
        String safeMessage = message == null ? "" : message;
        persistChatMessage(safeSender, safeMessage);
        renderChatMessage(safeSender, safeMessage);
    }

    /** Renders a saved or newly-created message without saving it twice. */
    private void renderChatMessage(String sender, String message) {
        if (isActivityDestroyed || chatContainer == null) {
            return;
        }

        LinearLayout messageCard = new LinearLayout(this);
        messageCard.setOrientation(LinearLayout.VERTICAL);
        messageCard.setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(8)
        );

        if ("You".equalsIgnoreCase(sender)) {
            messageCard.setBackgroundColor(Color.rgb(42, 42, 52));
        } else if ("P AI".equalsIgnoreCase(sender)) {
            messageCard.setBackgroundColor(Color.rgb(31, 35, 43));
        } else {
            messageCard.setBackgroundColor(Color.rgb(55, 39, 39));
        }

        TextView senderView = new TextView(this);
        senderView.setText(sender);
        senderView.setTextSize(13);
        senderView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        senderView.setTextColor(
                "P AI".equalsIgnoreCase(sender)
                        ? ACCENT_COLOR
                        : Color.LTGRAY
        );

        messageCard.addView(
                senderView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView messageView = new TextView(this);
        messageView.setText(message == null ? "" : message);
        messageView.setTextSize(15);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextIsSelectable(true);
        messageView.setLineSpacing(dp(2), 1.0f);
        messageView.setPadding(0, dp(5), 0, dp(3));

        messageCard.addView(
                messageView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        if ("P AI".equalsIgnoreCase(sender)
                && message != null
                && !message.trim().isEmpty()) {

            Button readButton = new Button(this);
            readButton.setText("🔊  Read aloud");
            readButton.setTextSize(12);
            readButton.setAllCaps(false);
            readButton.setTextColor(ACCENT_COLOR);
            readButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(45, 45, 53)
                    )
            );

            LinearLayout.LayoutParams readParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            readParams.topMargin = dp(5);

            messageCard.addView(readButton, readParams);

            readButton.setOnClickListener(
                    v -> handleReadAloudClick(message, readButton)
            );
        }

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, dp(5), 0, dp(5));

        chatContainer.addView(messageCard, cardParams);

        chatScroll.post(() ->
                chatScroll.fullScroll(ScrollView.FOCUS_DOWN)
        );
    }

    // ---------------------------------------------------------------------
    // Multiple conversations and private local storage
    // ---------------------------------------------------------------------

    private void loadConversations() {
        String saved = conversationsPreferences.getString(
                CONVERSATIONS_KEY, "[]");
        try {
            conversations = new JSONArray(saved);
        } catch (JSONException e) {
            conversations = new JSONArray();
        }

        activeConversationId = conversationsPreferences.getString(
                ACTIVE_CONVERSATION_KEY, null);

        // Migrate the previous single-chat history without deleting it.
        if (conversations.length() == 0) {
            String oldHistory = historyPreferences.getString(HISTORY_KEY, "[]");
            try {
                JSONArray oldMessages = new JSONArray(oldHistory);
                if (oldMessages.length() > 0) {
                    JSONObject migrated = new JSONObject();
                    String id = UUID.randomUUID().toString();
                    migrated.put("id", id);
                    migrated.put("title", deriveConversationTitle(oldMessages));
                    migrated.put("createdAt", System.currentTimeMillis());
                    migrated.put("updatedAt", System.currentTimeMillis());
                    migrated.put("messages", oldMessages);
                    conversations.put(migrated);
                    activeConversationId = id;
                }
            } catch (JSONException ignored) {
                // A damaged legacy history must not prevent app startup.
            }
        }

        if (findConversation(activeConversationId) == null
                && conversations.length() > 0) {
            JSONObject mostRecent = conversations.optJSONObject(
                    conversations.length() - 1);
            activeConversationId = mostRecent == null
                    ? null : mostRecent.optString("id", null);
        }

        if (conversations.length() == 0 || findConversation(activeConversationId) == null) {
            createConversationRecord("New chat");
        } else {
            loadActiveConversationMessages();
        }
        trimConversationLimit();
        saveConversations();
    }

    private JSONObject createConversationRecord(String title) {
        JSONObject conversation = new JSONObject();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        try {
            conversation.put("id", id);
            conversation.put("title", title);
            conversation.put("createdAt", now);
            conversation.put("updatedAt", now);
            conversation.put("messages", new JSONArray());
            conversations.put(conversation);
            activeConversationId = id;
            conversationHistory = conversation.optJSONArray("messages");
            trimConversationLimit();
            saveConversations();
            return conversation;
        } catch (JSONException e) {
            conversationHistory = new JSONArray();
            return null;
        }
    }

    private JSONObject findConversation(String id) {
        if (id == null || conversations == null) return null;
        for (int i = 0; i < conversations.length(); i++) {
            JSONObject item = conversations.optJSONObject(i);
            if (item != null && id.equals(item.optString("id", ""))) {
                return item;
            }
        }
        return null;
    }

    private void loadActiveConversationMessages() {
        JSONObject active = findConversation(activeConversationId);
        if (active == null) {
            conversationHistory = new JSONArray();
            return;
        }
        JSONArray messages = active.optJSONArray("messages");
        conversationHistory = messages == null ? new JSONArray() : messages;
    }

    private void saveConversations() {
        if (conversations == null) conversations = new JSONArray();
        conversationsPreferences.edit()
                .putString(CONVERSATIONS_KEY, conversations.toString())
                .putString(ACTIVE_CONVERSATION_KEY, activeConversationId == null ? "" : activeConversationId)
                .apply();
    }

    private void trimConversationLimit() {
        while (conversations.length() > MAX_CONVERSATIONS) {
            JSONObject oldest = conversations.optJSONObject(0);
            String oldestId = oldest == null ? "" : oldest.optString("id", "");
            // Never evict the active chat while enforcing the storage limit.
            conversations.remove(oldestId.equals(activeConversationId) ? 1 : 0);
        }
    }

    private String deriveConversationTitle(JSONArray messages) {
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.optJSONObject(i);
                if (item != null && "You".equalsIgnoreCase(
                        item.optString("sender", ""))) {
                    String text = item.optString("message", "").trim();
                    if (!text.isEmpty()) return shortenTitle(text);
                }
            }
        }
        return "Previous conversation";
    }

    private String shortenTitle(String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() > 38) return clean.substring(0, 35) + "...";
        return clean.isEmpty() ? "New chat" : clean;
    }

    private void restoreConversationHistory() {
        loadActiveConversationMessages();
        if (conversationHistory == null || conversationHistory.length() == 0) {
            addChatMessage("P AI",
                    "Hello! I am P AI, your personal assistant. How can I help you?");
            return;
        }
        for (int i = 0; i < conversationHistory.length(); i++) {
            JSONObject item = conversationHistory.optJSONObject(i);
            if (item != null) {
                renderChatMessage(item.optString("sender", "System"),
                        item.optString("message", ""));
            }
        }
    }

    private void persistChatMessage(String sender, String message) {
        if (conversationHistory == null) conversationHistory = new JSONArray();
        try {
            JSONObject item = new JSONObject();
            item.put("sender", sender);
            item.put("message", message);
            item.put("timestamp", System.currentTimeMillis());
            conversationHistory.put(item);
            while (conversationHistory.length() > MAX_SAVED_MESSAGES) {
                conversationHistory.remove(0);
            }

            JSONObject active = findConversation(activeConversationId);
            if (active != null) {
                active.put("messages", conversationHistory);
                active.put("updatedAt", System.currentTimeMillis());
                if ("New chat".equals(active.optString("title", "New chat"))
                        && "You".equalsIgnoreCase(sender)) {
                    active.put("title", shortenTitle(message));
                }
            }
            saveConversations();
        } catch (JSONException e) {
            showMessage("P AI could not save this message locally.");
        }
    }

    private void createNewConversation() {
        if (requestInProgress) {
            showMessage("Please wait for P AI to finish the current response.");
            return;
        }
        stopCurrentSpeech();
        createConversationRecord("New chat");
        chatContainer.removeAllViews();
        addChatMessage("P AI",
                "Hello! I am P AI, your personal assistant. How can I help you?");
        updateStatus("New conversation");
    }

    private void showConversationHistory() {
        if (requestInProgress) {
            showMessage("Please wait until the current response is complete.");
            return;
        }
        if (conversations == null || conversations.length() == 0) {
            showMessage("No saved conversations yet.");
            return;
        }

        final String[] ids = new String[conversations.length()];
        String[] titles = new String[conversations.length()];
        for (int i = conversations.length() - 1, display = 0; i >= 0; i--, display++) {
            JSONObject item = conversations.optJSONObject(i);
            ids[display] = item == null ? "" : item.optString("id", "");
            String title = item == null ? "Untitled chat" : item.optString("title", "New chat");
            titles[display] = (ids[display].equals(activeConversationId) ? "✓  " : "") + title;
        }

        new AlertDialog.Builder(this)
                .setTitle("CHAT HISTORY")
                .setItems(titles, (dialog, which) -> showConversationOptions(ids[which]))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showConversationOptions(String conversationId) {
        JSONObject selected = findConversation(conversationId);
        if (selected == null) return;
        String title = selected.optString("title", "New chat");
        String[] actions = {"OPEN CHAT", "RENAME CHAT", "DELETE CHAT"};
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) openConversation(conversationId);
                    else if (which == 1) renameConversation(conversationId);
                    else confirmDeleteConversation(conversationId);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void openConversation(String conversationId) {
        if (requestInProgress) return;
        JSONObject selected = findConversation(conversationId);
        if (selected == null) return;
        stopCurrentSpeech();
        activeConversationId = conversationId;
        loadActiveConversationMessages();
        chatContainer.removeAllViews();
        for (int i = 0; i < conversationHistory.length(); i++) {
            JSONObject item = conversationHistory.optJSONObject(i);
            if (item != null) renderChatMessage(
                    item.optString("sender", "System"),
                    item.optString("message", ""));
        }
        saveConversations();
        updateStatus("Opened: " + selected.optString("title", "Chat"));
    }

    private void renameConversation(String conversationId) {
        JSONObject selected = findConversation(conversationId);
        if (selected == null) return;
        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setText(selected.optString("title", "New chat"));
        titleInput.setSelection(titleInput.getText().length());
        int padding = dp(20);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(padding, dp(4), padding, 0);
        holder.addView(titleInput);
        new AlertDialog.Builder(this)
                .setTitle("RENAME CHAT")
                .setView(holder)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (dialog, which) -> {
                    String name = titleInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        showMessage("Chat title cannot be empty.");
                        return;
                    }
                    try {
                        selected.put("title", shortenTitle(name));
                        selected.put("updatedAt", System.currentTimeMillis());
                        saveConversations();
                        if (conversationId.equals(activeConversationId)) {
                            updateStatus("Chat renamed: " + shortenTitle(name));
                        }
                    } catch (JSONException e) {
                        showMessage("Unable to rename this chat.");
                    }
                })
                .show();
    }

    private void confirmDeleteConversation(String conversationId) {
        JSONObject selected = findConversation(conversationId);
        if (selected == null) return;
        String title = selected.optString("title", "this chat");
        new AlertDialog.Builder(this)
                .setTitle("DELETE CHAT?")
                .setMessage("Permanently delete \"" + title + "\"? This cannot be undone.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (dialog, which) -> deleteConversation(conversationId))
                .show();
    }

    private void deleteConversation(String conversationId) {
        if (requestInProgress) {
            showMessage("Wait for the current response before deleting a chat.");
            return;
        }
        int removeIndex = -1;
        for (int i = 0; i < conversations.length(); i++) {
            JSONObject item = conversations.optJSONObject(i);
            if (item != null && conversationId.equals(item.optString("id", ""))) {
                removeIndex = i;
                break;
            }
        }
        if (removeIndex < 0) return;
        boolean deletingActive = conversationId.equals(activeConversationId);
        conversations.remove(removeIndex);
        if (deletingActive) {
            if (conversations.length() == 0) {
                createConversationRecord("New chat");
            } else {
                JSONObject next = conversations.optJSONObject(conversations.length() - 1);
                activeConversationId = next == null ? null : next.optString("id", null);
                loadActiveConversationMessages();
            }
            chatContainer.removeAllViews();
            restoreConversationHistory();
        }
        saveConversations();
        updateStatus("Conversation deleted");
        showMessage("Chat deleted.");
    }

    // ---------------------------------------------------------------------
    // Status and utility methods
    // ---------------------------------------------------------------------

    private void updateStatus(String message) {
        if (!isActivityDestroyed && statusText != null) {
            statusText.setText(message);
        }
    }

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void showMessage(String message) {
        if (!isActivityDestroyed) {
            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );

            if (manager != null && messageInput != null) {
                manager.hideSoftInputFromWindow(
                        messageInput.getWindowToken(),
                        0
                );
            }
        } catch (Exception ignored) {
            // Keyboard visibility is not critical to app operation.
        }
    }

    // ---------------------------------------------------------------------
    // Text-to-speech initialization and user-controlled playback
    // ---------------------------------------------------------------------

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    if (isActivityDestroyed || textToSpeech == null) {
                        return;
                    }

                    if (status != TextToSpeech.SUCCESS) {
                        textToSpeechReady = false;
                        updateStatus("Text-to-speech is unavailable.");
                        return;
                    }

                    int languageResult =
                            textToSpeech.setLanguage(Locale.US);

                    textToSpeechReady =
                            languageResult != TextToSpeech.LANG_MISSING_DATA
                                    && languageResult
                                    != TextToSpeech.LANG_NOT_SUPPORTED;

                    if (textToSpeechReady) {
                        updateStatus("Ready");
                    } else {
                        updateStatus("English speech data is unavailable.");
                    }

                    textToSpeech.setOnUtteranceProgressListener(
                            new UtteranceProgressListener() {
                                @Override
                                public void onStart(String utteranceId) {
                                    // Playback state is reflected by the button.
                                }

                                @Override
                                public void onDone(String utteranceId) {
                                    finishSpeechIfCurrent(utteranceId);
                                }

                                @Override
                                public void onError(String utteranceId) {
                                    finishSpeechIfCurrent(utteranceId);
                                }

                                @Override
                                public void onError(
                                        String utteranceId,
                                        int errorCode
                                ) {
                                    finishSpeechIfCurrent(utteranceId);
                                }
                            }
                    );
                }
        );
    }

    private void handleReadAloudClick(String message, Button button) {
        if (!textToSpeechReady || textToSpeech == null) {
            showMessage(
                    "Speech is not ready yet. Please try again shortly."
            );
            return;
        }

        if (button == activeSpeechButton) {
            stopCurrentSpeech();
            return;
        }

        startReadingMessage(message, button);
    }

    private void startReadingMessage(String message, Button button) {
        if (textToSpeech == null || !textToSpeechReady) {
            showMessage("Text-to-speech is not available.");
            return;
        }

        stopCurrentSpeech();

        String cleanText = message == null ? "" : message.trim();

        if (cleanText.isEmpty()) {
            return;
        }

        activeSpeechRequestId = UUID.randomUUID().toString();
        activeSpeechButton = button;
        button.setText("■  Stop reading");

        List<String> chunks = splitTextForSpeech(
                cleanText,
                MAX_TTS_CHUNK_LENGTH
        );

        if (chunks.isEmpty()) {
            resetSpeechButton();
            return;
        }

        activeSpeechFinalUtteranceId =
                activeSpeechRequestId + "_part_" + (chunks.size() - 1);

        for (int i = 0; i < chunks.size(); i++) {
            String utteranceId =
                    activeSpeechRequestId + "_part_" + i;

            int queueMode = i == 0
                    ? TextToSpeech.QUEUE_FLUSH
                    : TextToSpeech.QUEUE_ADD;

            int result = textToSpeech.speak(
                    chunks.get(i),
                    queueMode,
                    null,
                    utteranceId
            );

            if (result == TextToSpeech.ERROR) {
                stopCurrentSpeech();
                showMessage("P AI could not start speaking this response.");
                return;
            }
        }
    }

    /**
     * Splits long responses into word-safe pieces so Android TTS does not
     * reject a response that exceeds its per-utterance length limit.
     */
    private List<String> splitTextForSpeech(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String remaining = text.trim();

        while (remaining.length() > maxLength) {
            int splitAt = remaining.lastIndexOf(' ', maxLength);

            if (splitAt <= 0) {
                splitAt = maxLength;
            }

            // Avoid splitting a UTF-16 surrogate pair.
            if (splitAt > 0
                    && splitAt < remaining.length()
                    && Character.isHighSurrogate(
                            remaining.charAt(splitAt - 1))
                    && Character.isLowSurrogate(
                            remaining.charAt(splitAt))) {
                splitAt--;
            }

            chunks.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private void finishSpeechIfCurrent(String utteranceId) {
        runOnUiThread(() -> {
            if (isActivityDestroyed) {
                return;
            }

            if (activeSpeechFinalUtteranceId != null
                    && activeSpeechFinalUtteranceId.equals(utteranceId)) {
                resetSpeechButton();
            }
        });
    }

    private void stopCurrentSpeech() {
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
            } catch (Exception ignored) {
                // Continue resetting the UI even if stop fails.
            }
        }

        resetSpeechButton();
    }

    private void resetSpeechButton() {
        if (activeSpeechButton != null) {
            activeSpeechButton.setText("🔊  Read aloud");
        }

        activeSpeechButton = null;
        activeSpeechRequestId = null;
        activeSpeechFinalUtteranceId = null;
    }

    // ---------------------------------------------------------------------
    // Microphone permission and manual online speech recognition
    // ---------------------------------------------------------------------

    private boolean hasAudioPermission() {
        return checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startManualVoiceRecognition() {
        if (!hasAudioPermission()) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST
            );
            updateStatus("Microphone permission is required for voice input.");
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showMessage(
                    "Speech recognition is unavailable on this device."
            );
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
                Locale.US
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to P AI"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
        );

        try {
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (Exception e) {
            showMessage("Unable to start voice recognition.");
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

        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("Microphone permission granted.");
                showMessage("You can now use voice input.");
            } else {
                updateStatus("Microphone permission denied.");
                showMessage(
                        "Microphone permission is needed for voice input."
                );
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST
                && resultCode == RESULT_OK
                && data != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                    );

            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);

                if (messageInput != null) {
                    messageInput.setText(spokenText);
                    messageInput.setSelection(
                            messageInput.getText().length()
                    );
                }

                sendMessage();
            }
        }
    }

    // ---------------------------------------------------------------------
    // AI chat request handling
    // ---------------------------------------------------------------------

    private void sendMessage() {
        if (isActivityDestroyed) {
            return;
        }

        if (requestInProgress) {
            showMessage("P AI is still preparing the previous response.");
            return;
        }

        String question = messageInput.getText()
                .toString()
                .trim();

        if (question.isEmpty()) {
            messageInput.setError("Enter a message first.");
            return;
        }

        hideKeyboard();

        // Handle supported local app-launch commands without using data.
        if (question.toLowerCase(Locale.ROOT).startsWith("open ")) {
            messageInput.setText("");
            addChatMessage("You", question);
            openInstalledApplication(question.substring(5).trim());
            return;
        }

        messageInput.setText("");
        addChatMessage("You", question);

        requestInProgress = true;
        sendButton.setEnabled(false);
        sendButton.setText("PLEASE WAIT...");
        updateStatus("P AI is thinking...");

        // Capture history on the UI thread so the request receives a stable
        // snapshot while the background network operation is running.
        JSONArray contextHistory = buildContextHistory(question);
        String contextHistoryJson = contextHistory.toString();

        try {
            aiExecutor.execute(
                    () -> performAIRequest(question, contextHistoryJson)
            );
        } catch (Exception e) {
            requestInProgress = false;
            restoreSendButton();

            addChatMessage(
                    "System",
                    "P AI could not start the request. Please try again."
            );

            updateStatus("Unable to start AI request.");
        }
    }

    private void performAIRequest(
            String question,
            String contextHistoryJson
    ) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

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
            requestBody.put(
                    "history",
                    new JSONArray(contextHistoryJson)
            );

            byte[] requestBytes = requestBody.toString().getBytes(
                    StandardCharsets.UTF_8
            );

            connection.setFixedLengthStreamingMode(requestBytes.length);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
                outputStream.flush();
            }

            int responseCode = connection.getResponseCode();

            InputStream responseStream =
                    responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (responseStream == null) {
                throw new Exception(
                        "The server did not return a response. Please try again."
                );
            }

            String responseText = readResponse(responseStream);

            JSONObject responseJson = new JSONObject(responseText);

            if (responseCode < 200 || responseCode >= 300) {
                String serverError = responseJson.optString(
                        "error",
                        "The AI service could not process your request."
                );

                throw new Exception(
                        "Server error (" + responseCode + "): " + serverError
                );
            }

            boolean success = responseJson.optBoolean("success", false);
            String reply = responseJson.optString("reply", "").trim();

            if (!success || reply.isEmpty()) {
                String serverError = responseJson.optString(
                        "error",
                        "The AI returned an empty or invalid response."
                );

                throw new Exception(serverError);
            }

            runOnUiThread(() -> {
                if (isActivityDestroyed) {
                    return;
                }

                addChatMessage("P AI", reply);
                updateStatus("Ready");
                requestInProgress = false;
                restoreSendButton();

                // Deliberately do not speak automatically.
                // The user must tap the speaker button on this response.
            });

        } catch (Exception e) {
            String errorMessage = e.getMessage();

            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage =
                        "An unexpected connection error occurred.";
            }

            final String displayError = errorMessage;

            runOnUiThread(() -> {
                if (isActivityDestroyed) {
                    return;
                }

                addChatMessage(
                        "System",
                        "I couldn't get a response: " + displayError
                );

                updateStatus("Connection failed");
                requestInProgress = false;
                restoreSendButton();
            });

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(InputStream inputStream) throws Exception {
        StringBuilder responseBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                )
        )) {
            String line;

            while ((line = reader.readLine()) != null) {
                // Preserve line boundaries in the JSON response body.
                responseBuilder.append(line).append('\n');
            }
        }

        return responseBuilder.toString();
    }

    private void restoreSendButton() {
        if (sendButton != null && !isActivityDestroyed) {
            sendButton.setEnabled(true);
            sendButton.setText("SEND MESSAGE");
        }
    }

    // ---------------------------------------------------------------------
    // Installed application launching
    // ---------------------------------------------------------------------

    private void openInstalledApplication(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            addChatMessage(
                    "P AI",
                    "Please specify the application you want to open."
            );
            return;
        }

        String requestedName = appName.trim();

        try {
            Intent launcherQuery = new Intent(Intent.ACTION_MAIN);
            launcherQuery.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> installedApps =
                    getPackageManager().queryIntentActivities(
                            launcherQuery,
                            0
                    );

            ResolveInfo exactMatch = null;
            ResolveInfo partialMatch = null;

            for (ResolveInfo app : installedApps) {
                if (app.activityInfo == null) {
                    continue;
                }

                String label = app.loadLabel(
                        getPackageManager()
                ).toString();

                if (label.equalsIgnoreCase(requestedName)) {
                    exactMatch = app;
                    break;
                }

                if (partialMatch == null
                        && label.toLowerCase(Locale.ROOT)
                        .contains(requestedName.toLowerCase(Locale.ROOT))) {
                    partialMatch = app;
                }
            }

            ResolveInfo selectedApp =
                    exactMatch != null ? exactMatch : partialMatch;

            if (selectedApp == null) {
                addChatMessage(
                        "P AI",
                        "I couldn't find an installed application named "
                                + requestedName + "."
                );
                return;
            }

            String packageName =
                    selectedApp.activityInfo.packageName;

            Intent launchIntent =
                    getPackageManager().getLaunchIntentForPackage(packageName);

            if (launchIntent == null) {
                addChatMessage(
                        "P AI",
                        "I found " + selectedApp.loadLabel(getPackageManager())
                                + ", but Android did not provide a launch action."
                );
                return;
            }

            addChatMessage(
                    "P AI",
                    "Opening " + selectedApp.loadLabel(getPackageManager()) + "..."
            );

            startActivity(launchIntent);

        } catch (Exception e) {
            addChatMessage(
                    "P AI",
                    "I couldn't open that application. Please try again."
            );
        }
    }
}
