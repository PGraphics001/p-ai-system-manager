package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class MainActivity extends Activity {

    // =========================================================
    // BRAND COLOURS
    // =========================================================

    private static final int BACKGROUND =
            Color.rgb(7, 13, 29);

    private static final int PANEL =
            Color.rgb(17, 29, 49);

    private static final int PANEL_LIGHT =
            Color.rgb(23, 39, 64);

    private static final int BLUE =
            Color.rgb(35, 115, 255);

    private static final int CYAN =
            Color.rgb(35, 205, 255);

    private static final int WHITE =
            Color.rgb(245, 249, 255);

    private static final int MUTED =
            Color.rgb(151, 169, 195);

    private static final int GREEN =
            Color.rgb(65, 215, 145);

    // =========================================================
    // USER INTERFACE
    // =========================================================

    private FrameLayout screenRoot;

    private LinearLayout mainLayout;

    private LinearLayout contentContainer;

    private TextView storageValue;
    private TextView ramValue;
    private TextView batteryValue;
    private TextView androidValue;

    private TextView statusMessage;

    // =========================================================
    // ACTIVITY LIFECYCLE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        getWindow().getDecorView().setSystemUiVisibility(0);

        buildDashboard();

        updateDeviceInformation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateDeviceInformation();
    }

    // =========================================================
    // MAIN DASHBOARD CONSTRUCTION
    // =========================================================

    private void buildDashboard() {

        screenRoot = new FrameLayout(this);

        screenRoot.setBackgroundColor(BACKGROUND);

        mainLayout = new LinearLayout(this);

        mainLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        mainLayout.setBackgroundColor(BACKGROUND);

        screenRoot.addView(
                mainLayout,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        // TOP NAVIGATION
        buildTopBar();

        // SCROLLABLE DASHBOARD
        ScrollView scrollView = new ScrollView(this);

        scrollView.setFillViewport(true);

        scrollView.setVerticalScrollBarEnabled(false);

        contentContainer = new LinearLayout(this);

        contentContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        contentContainer.setPadding(
                dp(18),
                dp(15),
                dp(18),
                dp(20)
        );

        scrollView.addView(
                contentContainer,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        mainLayout.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        buildWelcomeSection();

        buildDeviceOverview();

        buildFeatureSection();

        buildRecentActivitySection();

        // BOTTOM NAVIGATION
        buildBottomNavigation();

        setContentView(screenRoot);
    }

    // =========================================================
    // TOP NAVIGATION BAR
    // =========================================================

    private void buildTopBar() {

        LinearLayout topBar = new LinearLayout(this);

        topBar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        topBar.setGravity(Gravity.CENTER_VERTICAL);

        topBar.setPadding(
                dp(15),
                dp(12),
                dp(15),
                dp(12)
        );

        topBar.setBackgroundColor(BACKGROUND);

        // MENU BUTTON
        TextView menuButton = createText(
                "☰",
                27,
                CYAN,
                true
        );

        menuButton.setGravity(Gravity.CENTER);

        menuButton.setContentDescription(
                "Open navigation menu"
        );

        topBar.addView(
                menuButton,
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(45)
                )
        );

        menuButton.setOnClickListener(
                v -> showNavigationDrawer()
        );

        // OFFICIAL LOGO
        ImageView logo = new ImageView(this);

        logo.setImageResource(
                R.drawable.p_ai_launcher
        );

        logo.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );

        logoParams.leftMargin = dp(8);

        topBar.addView(logo, logoParams);

        // BRAND TEXT
        LinearLayout brandContainer =
                new LinearLayout(this);

        brandContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        brandContainer.setPadding(
                dp(8),
                0,
                0,
                0
        );

        TextView brand = createText(
                "P AI",
                19,
                WHITE,
                true
        );

        TextView subtitle = createText(
                "SYSTEM MANAGER",
                9,
                CYAN,
                true
        );

        subtitle.setLetterSpacing(0.12f);

        brandContainer.addView(brand);

        brandContainer.addView(subtitle);

        topBar.addView(
                brandContainer,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        // ACCOUNT BUTTON
        TextView accountButton = createText(
                "◉",
                25,
                CYAN,
                true
        );

        accountButton.setGravity(Gravity.CENTER);

        accountButton.setContentDescription(
                "Account settings"
        );

        topBar.addView(
                accountButton,
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(45)
                )
        );

        accountButton.setOnClickListener(
                v -> openAccountSettings()
        );

        mainLayout.addView(
                topBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        View divider = new View(this);

        divider.setBackgroundColor(
                Color.rgb(29, 53, 82)
        );

        mainLayout.addView(
                divider,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );
    }

    // =========================================================
    // WELCOME SECTION
    // =========================================================

    private void buildWelcomeSection() {

        TextView greeting = createText(
                "Welcome to P AI",
                25,
                WHITE,
                true
        );

        contentContainer.addView(greeting);

        TextView description = createText(
                "Your intelligent personal assistant and device manager.",
                13,
                MUTED,
                false
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.topMargin = dp(7);

        contentContainer.addView(
                description,
                descriptionParams
        );

        // STATUS CARD
        LinearLayout statusCard = new LinearLayout(this);

        statusCard.setOrientation(
                LinearLayout.VERTICAL
        );

        statusCard.setPadding(
                dp(16),
                dp(15),
                dp(16),
                dp(15)
        );

        statusCard.setBackground(
                roundedBackground(PANEL_LIGHT, 16, CYAN)
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.topMargin = dp(20);

        contentContainer.addView(
                statusCard,
                cardParams
        );

        LinearLayout statusRow = new LinearLayout(this);

        statusRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView indicator = createText(
                "●",
                16,
                GREEN,
                true
        );

        statusRow.addView(indicator);

        TextView statusTitle = createText(
                "  P AI SYSTEM MANAGER",
                14,
                WHITE,
                true
        );

        statusRow.addView(statusTitle);

        statusCard.addView(statusRow);

        statusMessage = createText(
                "Dashboard ready",
                12,
                MUTED,
                false
        );

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        messageParams.topMargin = dp(7);

        statusCard.addView(
                statusMessage,
                messageParams
        );
    }

    // =========================================================
    // DEVICE OVERVIEW
    // =========================================================

    private void buildDeviceOverview() {

        addSectionTitle(
                "Device Overview",
                "Your device information"
        );

        LinearLayout firstRow = new LinearLayout(this);

        firstRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        storageValue = createText(
                "--",
                23,
                CYAN,
                true
        );

        ramValue = createText(
                "--",
                23,
                CYAN,
                true
        );

        firstRow.addView(
                createInformationCard(
                        "▤",
                        "Storage",
                        storageValue,
                        "Available space"
                ),
                weightedParams()
        );

        firstRow.addView(
                createInformationCard(
                        "▦",
                        "Memory",
                        ramValue,
                        "RAM usage"
                ),
                weightedParams()
        );

        contentContainer.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);

        secondRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        batteryValue = createText(
                "--",
                23,
                CYAN,
                true
        );

        androidValue = createText(
                "--",
                23,
                CYAN,
                true
        );

        secondRow.addView(
                createInformationCard(
                        "▰",
                        "Battery",
                        batteryValue,
                        "Battery level"
                ),
                weightedParams()
        );

        secondRow.addView(
                createInformationCard(
                        "◈",
                        "Android",
                        androidValue,
                        "Operating system"
                ),
                weightedParams()
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        rowParams.topMargin = dp(10);

        contentContainer.addView(
                secondRow,
                rowParams
        );
    }

    // =========================================================
    // END OF PART 1
    // =========================================================

    // =========================================================
    // INFORMATION CARD
    // =========================================================

    private View createInformationCard(
            String icon,
            String title,
            TextView value,
            String description
    ) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(13),
                dp(14),
                dp(10),
                dp(14)
        );

        card.setBackground(
                roundedBackground(PANEL, 14, PANEL_LIGHT)
        );

        TextView iconView = createText(
                icon,
                21,
                CYAN,
                true
        );

        card.addView(iconView);

        TextView titleView = createText(
                title,
                12,
                MUTED,
                true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dp(9);

        card.addView(titleView, titleParams);

        value.setSingleLine(true);

        value.setTextSize(21);

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.topMargin = dp(5);

        card.addView(value, valueParams);

        TextView descriptionView = createText(
                description,
                10,
                MUTED,
                false
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.topMargin = dp(4);

        card.addView(
                descriptionView,
                descriptionParams
        );

        return card;
    }

    // =========================================================
    // FEATURE SECTION
    // =========================================================

    private void buildFeatureSection() {

        addSectionTitle(
                "Quick Access",
                "Manage your phone and access P AI tools"
        );

        LinearLayout firstRow = new LinearLayout(this);

        firstRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        firstRow.addView(
                createFeatureCard(
                        "✦",
                        "AI Chat",
                        "Talk with P AI",
                        CYAN,
                        this::openChat
                ),
                weightedParams()
        );

        firstRow.addView(
                createFeatureCard(
                        "▦",
                        "App Manager",
                        "Manage applications",
                        BLUE,
                        this::openAppManager
                ),
                weightedParams()
        );

        contentContainer.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);

        secondRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        secondRow.addView(
                createFeatureCard(
                        "◈",
                        "Device Manager",
                        "Device information",
                        CYAN,
                        this::openDeviceManager
                ),
                weightedParams()
        );

        secondRow.addView(
                createFeatureCard(
                        "♫",
                        "Voice Assistant",
                        "Voice features",
                        BLUE,
                        this::openVoiceAssistant
                ),
                weightedParams()
        );

        LinearLayout.LayoutParams secondParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        secondParams.topMargin = dp(10);

        contentContainer.addView(
                secondRow,
                secondParams
        );

        LinearLayout thirdRow = new LinearLayout(this);

        thirdRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        thirdRow.addView(
                createFeatureCard(
                        "⚙",
                        "Tools",
                        "Explore utilities",
                        CYAN,
                        this::openTools
                ),
                weightedParams()
        );

        thirdRow.addView(
                createFeatureCard(
                        "◉",
                        "Preferences",
                        "Personalise P AI",
                        BLUE,
                        this::openPreferences
                ),
                weightedParams()
        );

        LinearLayout.LayoutParams thirdParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        thirdParams.topMargin = dp(10);

        contentContainer.addView(
                thirdRow,
                thirdParams
        );
    }

    // =========================================================
    // FEATURE CARD
    // =========================================================

    private View createFeatureCard(
            String icon,
            String title,
            String description,
            int iconColor,
            Runnable action
    ) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(Gravity.CENTER_VERTICAL);

        card.setPadding(
                dp(13),
                dp(16),
                dp(10),
                dp(16)
        );

        card.setBackground(
                roundedBackground(PANEL, 15, PANEL_LIGHT)
        );

        card.setClickable(true);

        card.setFocusable(true);

        TextView iconView = createText(
                icon,
                25,
                iconColor,
                true
        );

        card.addView(iconView);

        TextView titleView = createText(
                title,
                13,
                WHITE,
                true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dp(11);

        card.addView(titleView, titleParams);

        TextView descriptionView = createText(
                description,
                10,
                MUTED,
                false
        );

        descriptionView.setMaxLines(2);

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.topMargin = dp(5);

        card.addView(
                descriptionView,
                descriptionParams
        );

        card.setOnClickListener(v -> action.run());

        return card;
    }

    // =========================================================
    // RECENT ACTIVITY
    // =========================================================

    private void buildRecentActivitySection() {

        addSectionTitle(
                "Recent Activity",
                "Your dashboard activity"
        );

        LinearLayout activityCard = new LinearLayout(this);

        activityCard.setOrientation(
                LinearLayout.VERTICAL
        );

        activityCard.setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(15)
        );

        activityCard.setBackground(
                roundedBackground(PANEL, 15, PANEL_LIGHT)
        );

        TextView activity = createText(
                "●  Dashboard opened",
                13,
                WHITE,
                false
        );

        activityCard.addView(activity);

        TextView activityDescription = createText(
                "P AI System Manager is ready for use.",
                11,
                MUTED,
                false
        );

        LinearLayout.LayoutParams activityParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        activityParams.topMargin = dp(8);

        activityCard.addView(
                activityDescription,
                activityParams
        );

        contentContainer.addView(activityCard);
    }

    // =========================================================
    // BOTTOM NAVIGATION
    // =========================================================

    private void buildBottomNavigation() {

        LinearLayout navigation = new LinearLayout(this);

        navigation.setOrientation(
                LinearLayout.HORIZONTAL
        );

        navigation.setGravity(
                Gravity.CENTER_VERTICAL
        );

        navigation.setBackgroundColor(
                Color.rgb(12, 22, 39)
        );

        navigation.setPadding(
                dp(5),
                dp(8),
                dp(5),
                dp(8)
        );

        addBottomItem(
                navigation,
                "⌂",
                "Home",
                true,
                this::showDashboard
        );

        addBottomItem(
                navigation,
                "◌",
                "Chat",
                false,
                this::openChat
        );

        addBottomItem(
                navigation,
                "▦",
                "Tools",
                false,
                this::openTools
        );

        addBottomItem(
                navigation,
                "⚙",
                "Settings",
                false,
                this::openPreferences
        );

        mainLayout.addView(
                navigation,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(66)
                )
        );
    }

    private void addBottomItem(
            LinearLayout parent,
            String icon,
            String label,
            boolean selected,
            Runnable action
    ) {

        LinearLayout item = new LinearLayout(this);

        item.setOrientation(
                LinearLayout.VERTICAL
        );

        item.setGravity(Gravity.CENTER);

        TextView iconView = createText(
                icon,
                22,
                selected ? CYAN : MUTED,
                true
        );

        TextView labelView = createText(
                label,
                10,
                selected ? CYAN : MUTED,
                true
        );

        item.addView(iconView);

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        labelParams.topMargin = dp(3);

        item.addView(labelView, labelParams);

        item.setOnClickListener(v -> action.run());

        parent.addView(
                item,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                )
        );
    }

    // =========================================================
    // NAVIGATION DRAWER
    // =========================================================

    private void showNavigationDrawer() {

        final String[] menuItems = {
                "Dashboard",
                "AI Chat",
                "App Manager",
                "Device Manager",
                "Voice Assistant",
                "Tools",
                "Account Settings",
                "Preferences",
                "Help and About",
                "Log Out"
        };

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        LinearLayout drawer = new LinearLayout(this);

        drawer.setOrientation(
                LinearLayout.VERTICAL
        );

        drawer.setPadding(
                dp(18),
                dp(22),
                dp(18),
                dp(15)
        );

        drawer.setBackgroundColor(BACKGROUND);

        ImageView logo = new ImageView(this);

        logo.setImageResource(
                R.drawable.p_ai_launcher
        );

        logo.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(
                        dp(90),
                        dp(90)
                );

        logoParams.gravity = Gravity.CENTER_HORIZONTAL;

        drawer.addView(logo, logoParams);

        TextView heading = createText(
                "P AI SYSTEM MANAGER",
                17,
                WHITE,
                true
        );

        heading.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        headingParams.topMargin = dp(7);

        headingParams.bottomMargin = dp(18);

        drawer.addView(heading, headingParams);

        AlertDialog dialog = builder.create();

        for (int i = 0; i < menuItems.length; i++) {

            final int selectedIndex = i;

            TextView menuItem = createText(
                    menuItems[i],
                    14,
                    i == 9 ? Color.rgb(255, 110, 110) : WHITE,
                    i == 0
            );

            menuItem.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            menuItem.setPadding(
                    dp(14),
                    dp(13),
                    dp(10),
                    dp(13)
            );

            menuItem.setBackground(
                    roundedBackground(PANEL, 10, PANEL)
            );

            LinearLayout.LayoutParams itemParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            itemParams.bottomMargin = dp(6);

            drawer.addView(menuItem, itemParams);

            menuItem.setOnClickListener(v -> {

                dialog.dismiss();

                handleDrawerSelection(selectedIndex);
            });
        }

        ScrollView drawerScroll = new ScrollView(this);

        drawerScroll.addView(drawer);

        dialog.setView(
                drawerScroll,
                dp(12),
                dp(8),
                dp(12),
                dp(8)
        );

        dialog.show();
    }

    // =========================================================
    // END OF PART 2
    // =========================================================

    // =========================================================
    // DRAWER NAVIGATION ACTIONS
    // =========================================================

    private void handleDrawerSelection(int position) {

        switch (position) {

            case 0:
                showDashboard();
                break;

            case 1:
                openChat();
                break;

            case 2:
                openAppManager();
                break;

            case 3:
                openDeviceManager();
                break;

            case 4:
                openVoiceAssistant();
                break;

            case 5:
                openTools();
                break;

            case 6:
                openAccountSettings();
                break;

            case 7:
                openPreferences();
                break;

            case 8:
                showHelpAndAbout();
                break;

            case 9:
                showLogoutNotice();
                break;

            default:
                break;
        }
    }

    // =========================================================
    // DASHBOARD NAVIGATION
    // =========================================================

    private void showDashboard() {

        Toast.makeText(
                this,
                "You are on the dashboard.",
                Toast.LENGTH_SHORT
        ).show();
    }

    // =========================================================
    // CHAT NAVIGATION
    // =========================================================

    private void openChat() {

        try {

            Intent intent = new Intent();

            intent.setClassName(
                    this,
                    getPackageName() + ".ChatActivity"
            );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "The AI Chat screen is being integrated.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // APP MANAGER
    // =========================================================

    private void openAppManager() {

        try {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS
            );

            startActivity(intent);

        } catch (Exception e) {

            showMessage(
                    "Application settings are unavailable on this device."
            );
        }
    }

    // =========================================================
    // DEVICE MANAGER
    // =========================================================

    private void openDeviceManager() {

        try {

            startActivity(
                    new Intent(Settings.ACTION_SETTINGS)
            );

        } catch (Exception e) {

            showMessage(
                    "Device settings could not be opened."
            );
        }
    }

    // =========================================================
    // VOICE ASSISTANT
    // =========================================================

    private void openVoiceAssistant() {

        showFeatureNotice(
                "Voice Assistant",
                "Voice assistant controls will be integrated in a dedicated development stage. Background Vosk listening will not be started from the chat screen."
        );
    }

    // =========================================================
    // TOOLS
    // =========================================================

    private void openTools() {

        final String[] tools = {
                "Storage Information",
                "Battery Information",
                "Android Information",
                "System Settings"
        };

        new AlertDialog.Builder(this)
                .setTitle("P AI Tools")
                .setItems(tools, (dialog, which) -> {

                    switch (which) {

                        case 0:
                            showStorageInformation();
                            break;

                        case 1:
                            showBatteryInformation();
                            break;

                        case 2:
                            showAndroidInformation();
                            break;

                        case 3:
                            openDeviceManager();
                            break;
                    }

                })
                .setNegativeButton("Close", null)
                .show();
    }

    // =========================================================
    // PREFERENCES
    // =========================================================

    private void openPreferences() {

        final String[] options = {
                "Appearance",
                "Chat Preferences",
                "Voice Preferences",
                "Tools Preferences",
                "Device Preferences",
                "Account Settings"
        };

        new AlertDialog.Builder(this)
                .setTitle("P AI Preferences")
                .setItems(options, (dialog, which) -> {

                    String selected = options[which];

                    showFeatureNotice(
                            selected,
                            "This preference section will be implemented in its dedicated development stage."
                    );

                })
                .setNegativeButton("Close", null)
                .show();
    }

    // =========================================================
    // ACCOUNT SETTINGS
    // =========================================================

    private void openAccountSettings() {

        showFeatureNotice(
                "Account Settings",
                "Your existing authentication system is preserved. Account management will be integrated without replacing AuthSessionManager."
        );
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    private void showLogoutNotice() {

        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage(
                        "The secure logout action will be connected to your existing AuthSessionManager. We will not clear your session using an unverified method."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {

                    showMessage(
                            "Secure logout integration is pending."
                    );

                })
                .show();
    }

    // =========================================================
    // HELP AND ABOUT
    // =========================================================

    private void showHelpAndAbout() {

        new AlertDialog.Builder(this)
                .setTitle("About P AI System Manager")
                .setMessage(
                        "P AI System Manager\n\nYour intelligent personal assistant and device manager.\n\nVersion 1.1\n\nDeveloped by P Graphics."
                )
                .setPositiveButton("Close", null)
                .show();
    }

    // =========================================================
    // DEVICE INFORMATION
    // =========================================================

    private void updateDeviceInformation() {

        updateStorageInformation();

        updateRamInformation();

        updateBatteryInformation();

        updateAndroidInformation();
    }

    // =========================================================
    // STORAGE
    // =========================================================

    private void updateStorageInformation() {

        try {

            StatFs stat = new StatFs(
                    Environment.getDataDirectory().getPath()
            );

            long available =
                    stat.getAvailableBytes();

            storageValue.setText(
                    formatStorage(available)
            );

        } catch (Exception e) {

            storageValue.setText("--");
        }
    }

    private void showStorageInformation() {

        try {

            StatFs stat = new StatFs(
                    Environment.getDataDirectory().getPath()
            );

            long total = stat.getTotalBytes();

            long available = stat.getAvailableBytes();

            long used = total - available;

            String message =
                    "Total storage: " + formatStorage(total)
                    + "\nUsed storage: " + formatStorage(used)
                    + "\nAvailable storage: " + formatStorage(available);

            showInformationDialog(
                    "Storage Information",
                    message
            );

        } catch (Exception e) {

            showMessage(
                    "Unable to read storage information."
            );
        }
    }

    // =========================================================
    // RAM
    // =========================================================

    private void updateRamInformation() {

        try {

            ActivityManager manager =
                    (ActivityManager) getSystemService(
                            ACTIVITY_SERVICE
                    );

            ActivityManager.MemoryInfo info =
                    new ActivityManager.MemoryInfo();

            manager.getMemoryInfo(info);

            long total = info.totalMem;

            long available = info.availMem;

            long used = total - available;

            int percentage = total > 0
                    ? (int) ((used * 100L) / total)
                    : 0;

            ramValue.setText(
                    percentage + "%"
            );

        } catch (Exception e) {

            ramValue.setText("--");
        }
    }

    // =========================================================
    // BATTERY
    // =========================================================

    private void updateBatteryInformation() {

        try {

            Intent batteryIntent = registerReceiver(
                    null,
                    new android.content.IntentFilter(
                            Intent.ACTION_BATTERY_CHANGED
                    )
            );

            if (batteryIntent == null) {

                batteryValue.setText("--");

                return;
            }

            int level = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_LEVEL,
                    -1
            );

            int scale = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_SCALE,
                    -1
            );

            if (level >= 0 && scale > 0) {

                int percentage =
                        (level * 100) / scale;

                batteryValue.setText(
                        percentage + "%"
                );

            } else {

                batteryValue.setText("--");
            }

        } catch (Exception e) {

            batteryValue.setText("--");
        }
    }

    // =========================================================
    // ANDROID VERSION
    // =========================================================

    private void updateAndroidInformation() {

        androidValue.setText(
                "Android " + Build.VERSION.RELEASE
        );
    }

    private void showBatteryInformation() {

        showInformationDialog(
                "Battery Information",
                "Current battery level: "
                        + batteryValue.getText().toString()
        );
    }

    private void showAndroidInformation() {

        showInformationDialog(
                "Android Information",
                "Android version: "
                        + Build.VERSION.RELEASE
                        + "\nAPI level: "
                        + Build.VERSION.SDK_INT
                        + "\nDevice manufacturer: "
                        + Build.MANUFACTURER
                        + "\nDevice model: "
                        + Build.MODEL
        );
    }

    // =========================================================
    // GENERAL INFORMATION DIALOG
    // =========================================================

    private void showInformationDialog(
            String title,
            String message
    ) {

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    // =========================================================
    // FEATURE NOTICE
    // =========================================================

    private void showFeatureNotice(
            String title,
            String message
    ) {

        showInformationDialog(
                title,
                message
        );
    }

    // =========================================================
    // SECTION TITLE
    // =========================================================

    private void addSectionTitle(
            String title,
            String subtitle
    ) {

        TextView titleView = createText(
                title,
                18,
                WHITE,
                true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dp(24);

        contentContainer.addView(
                titleView,
                titleParams
        );

        TextView subtitleView = createText(
                subtitle,
                11,
                MUTED,
                false
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin = dp(5);

        subtitleParams.bottomMargin = dp(12);

        contentContainer.addView(
                subtitleView,
                subtitleParams
        );
    }

    // =========================================================
    // TEXT VIEW FACTORY
    // =========================================================

    private TextView createText(
            String text,
            int size,
            int color,
            boolean bold
    ) {

        TextView view = new TextView(this);

        view.setText(text);

        view.setTextSize(size);

        view.setTextColor(color);

        view.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {

            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    // =========================================================
    // BACKGROUND FACTORY
    // =========================================================

    private GradientDrawable roundedBackground(
            int backgroundColor,
            int radius,
            int strokeColor
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(backgroundColor);

        drawable.setCornerRadius(
                dp(radius)
        );

        drawable.setStroke(
                dp(1),
                strokeColor
        );

        return drawable;
    }

    // =========================================================
    // WEIGHTED LAYOUT PARAMETERS
    // =========================================================

    private LinearLayout.LayoutParams weightedParams() {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                );

        params.setMargins(
                dp(4),
                0,
                dp(4),
                0
        );

        return params;
    }

    // =========================================================
    // STORAGE FORMATTER
    // =========================================================

    private String formatStorage(long bytes) {

        double gb = bytes / (1024.0 * 1024.0 * 1024.0);

        DecimalFormat format =
                new DecimalFormat("0.0");

        return format.format(gb) + " GB";
    }

    // =========================================================
    // DISPLAY DIMENSIONS
    // =========================================================

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    // =========================================================
    // USER MESSAGE
    // =========================================================

    private void showMessage(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    // =========================================================
    // END OF MAINACTIVITY.JAVA
    // =========================================================
}
