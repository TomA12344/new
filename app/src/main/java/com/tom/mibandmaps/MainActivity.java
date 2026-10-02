package com.tom.mibandmaps;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String CHANNEL_ID = "maps_navigation_bridge";
    private static final String PREFS = "diag";

    private TextView notificationStatus;
    private TextView listenerStatus;
    private TextView diagnosticStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        createChannel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(44), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = text("Mi Band Maps 1.1", 28, true);
        root.addView(title);

        TextView intro = text(
                "Google-Maps-Abbiegehinweise → Android-Benachrichtigung → Mi Fitness → Mi Band 9. Kein eigenes GPS und kein Polling.",
                16, false);
        intro.setPadding(0, dp(10), 0, dp(20));
        root.addView(intro);

        notificationStatus = text("", 17, true);
        root.addView(notificationStatus);

        Button notificationButton = button("1. Benachrichtigungen erlauben");
        notificationButton.setOnClickListener(v -> requestNotifications());
        root.addView(notificationButton);

        listenerStatus = text("", 17, true);
        listenerStatus.setPadding(0, dp(16), 0, 0);
        root.addView(listenerStatus);

        Button accessButton = button("2. Benachrichtigungszugriff öffnen");
        accessButton.setOnClickListener(v -> openListenerSettings());
        root.addView(accessButton);

        Button testButton = button("3. Testnachricht senden");
        testButton.setOnClickListener(v -> sendTestNotification());
        root.addView(testButton);

        diagnosticStatus = text("", 14, false);
        diagnosticStatus.setPadding(0, dp(18), 0, 0);
        root.addView(diagnosticStatus);

        Button refreshButton = button("Status aktualisieren");
        refreshButton.setOnClickListener(v -> updateStatus());
        root.addView(refreshButton);

        TextView finish = text(
                "Wenn die Testnachricht auf dem Band erscheint, funktioniert App → Mi Fitness → Band. Danach Google Maps starten und hier auf „Status aktualisieren“ tippen.",
                14, false);
        finish.setPadding(0, dp(18), 0, 0);
        root.addView(finish);

        return root;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.rgb(25, 25, 25));
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        return b;
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        } else {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        }
    }

    private void openListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            intent.putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(this, MapsNotificationListener.class).flattenToString());
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private void sendTestNotification() {
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_navigation)
                .setContentTitle("Google Maps")
                .setContentText("Test: In 200 m rechts abbiegen")
                .setStyle(new Notification.BigTextStyle().bigText("Test: In 200 m rechts abbiegen"))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setShowWhen(false)
                .build();
        getSystemService(NotificationManager.class).notify(9002, n);
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Google Maps auf Mi Band",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Aktuelle Abbiegehinweise aus Google Maps");
        channel.setSound(null, null);
        channel.enableVibration(false);
        nm.createNotificationChannel(channel);
    }

    private void updateStatus() {
        boolean notificationsGranted =
                Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        notificationStatus.setText(
                notificationsGranted ? "✓ Benachrichtigungen erlaubt" : "○ Benachrichtigungen noch nicht erlaubt");

        boolean listenerGranted = false;
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            listenerGranted = nm.isNotificationListenerAccessGranted(
                    new ComponentName(this, MapsNotificationListener.class));
        }
        listenerStatus.setText(
                listenerGranted ? "✓ Benachrichtigungszugriff aktiv" : "○ Benachrichtigungszugriff noch nicht aktiv");

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        long connected = p.getLong("connected_at", 0L);
        long mapsSeen = p.getLong("maps_seen_at", 0L);
        String mapsText = p.getString("maps_text", "");

        StringBuilder s = new StringBuilder();
        if (connected > 0) {
            s.append("Listener verbunden: ja\n");
        } else {
            s.append("Listener verbunden: noch nicht erkannt\n");
        }

        if (mapsSeen > 0) {
            s.append("Google-Maps-Meldung empfangen: ja");
            if (mapsText != null && !mapsText.isEmpty()) {
                s.append("\nZuletzt: ").append(mapsText);
            }
        } else {
            s.append("Google-Maps-Meldung empfangen: noch nicht");
        }
        diagnosticStatus.setText(s.toString());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
