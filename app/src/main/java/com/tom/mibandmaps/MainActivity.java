package com.tom.mibandmaps;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView notificationStatus;
    private TextView listenerStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(56), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = text("Mi Band Maps", 28, true);
        root.addView(title);

        TextView intro = text(
                "Leitet Google-Maps-Abbiegehinweise als Benachrichtigung an dein Mi Band 9 weiter. Kein eigenes GPS, kein Dauer-Polling.",
                16, false);
        intro.setPadding(0, dp(10), 0, dp(24));
        root.addView(intro);

        notificationStatus = text("", 17, true);
        root.addView(notificationStatus);

        Button notificationButton = button("1. Benachrichtigungen erlauben");
        notificationButton.setOnClickListener(v -> requestNotifications());
        root.addView(notificationButton);

        listenerStatus = text("", 17, true);
        listenerStatus.setPadding(0, dp(20), 0, 0);
        root.addView(listenerStatus);

        Button accessButton = button("2. Benachrichtigungszugriff öffnen");
        accessButton.setOnClickListener(v -> openListenerSettings());
        root.addView(accessButton);

        TextView finish = text(
                "Danach in Mi Fitness → Mi Band 9 → App-Benachrichtigungen „Mi Band Maps“ aktivieren und Google Maps Navigation starten.",
                14, false);
        finish.setPadding(0, dp(20), 0, 0);
        root.addView(finish);

        return root;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.rgb(25, 25, 25));
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
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
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
