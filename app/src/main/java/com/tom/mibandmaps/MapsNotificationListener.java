package com.tom.mibandmaps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class MapsNotificationListener extends NotificationListenerService {
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String CHANNEL_ID = "maps_navigation_bridge";
    private static final int BRIDGE_NOTIFICATION_ID = 9001;
    private static final String PREFS = "diag";

    private String lastBody = "";
    private String lastManeuverKey = "";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        createChannel();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("connected_at", System.currentTimeMillis())
                .apply();

        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn != null && MAPS_PACKAGE.equals(sbn.getPackageName())) {
                        handleMapsNotification(sbn);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAPS_PACKAGE.equals(sbn.getPackageName())) return;
        handleMapsNotification(sbn);
    }

    private void handleMapsNotification(StatusBarNotification sbn) {
        Notification source = sbn.getNotification();
        if (source == null) return;

        String body = extractNavigationText(source);
        String diagnostic = body.isEmpty() ? "(Google Maps erkannt, aber kein Text gefunden)" : body;

        SharedPreferences.Editor edit = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        edit.putLong("maps_seen_at", System.currentTimeMillis());
        edit.putString("maps_text", diagnostic);
        edit.apply();

        if (body.isEmpty()) return;
        if (body.equals(lastBody)) return;

        String maneuverKey = maneuverKey(body);

        // Google Maps aktualisiert die Restentfernung sehr häufig. Solange sich
        // nur Entfernung / ETA ändern, bekommt das Band KEINE neue Meldung.
        if (!lastManeuverKey.isEmpty() && maneuverKey.equals(lastManeuverKey)) {
            lastBody = body;
            return;
        }

        lastBody = body;
        lastManeuverKey = maneuverKey;
        publish(body);
    }

    private String extractNavigationText(Notification n) {
        Set<String> parts = new LinkedHashSet<>();
        Bundle extras = n.extras;

        if (extras != null) {
            add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
            add(parts, extras.getCharSequence(Notification.EXTRA_TITLE));
            add(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            add(parts, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
            add(parts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));

            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                for (CharSequence line : lines) add(parts, line);
            }

            for (String key : extras.keySet()) {
                Object value;
                try {
                    value = extras.get(key);
                } catch (Exception e) {
                    continue;
                }
                if (value instanceof CharSequence) {
                    add(parts, (CharSequence) value);
                } else if (value instanceof CharSequence[]) {
                    for (CharSequence c : (CharSequence[]) value) add(parts, c);
                }
            }
        }

        add(parts, n.tickerText);

        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.length() > 160) continue;
            if (out.length() > 0) out.append(" — ");
            out.append(p);
            if (out.length() > 220) break;
        }

        String result = out.toString().trim();
        if (result.length() > 240) result = result.substring(0, 240);
        return result;
    }

    private void add(Set<String> parts, CharSequence value) {
        String s = clean(value);
        if (!s.isEmpty() && !s.equalsIgnoreCase("Google Maps") && !s.equalsIgnoreCase("Maps")) {
            parts.add(s);
        }
    }

    private String clean(CharSequence value) {
        if (value == null) return "";
        return value.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String maneuverKey(String value) {
        String s = value.toLowerCase(Locale.ROOT);

        // Entfernungen: 90 m, 1,2 km, 500 ft, 0.3 mi ...
        s = s.replaceAll(
                "\\b\\d+(?:[.,]\\d+)?\\s*(?:m|km|ft|mi|meter|meters|metre|metres|kilometer|kilometers|kilometre|kilometres)\\b",
                "#dist");

        // Restzeit / Fahrtdauer: 8 min, 1 h, 2 Stunden ...
        s = s.replaceAll(
                "\\b\\d+(?:[.,]\\d+)?\\s*(?:min|mins|minute|minutes|h|hr|hrs|hour|hours|std|stunde|stunden)\\b",
                "#time");

        // ETA/Uhrzeit: 08:42, 17:05 ...
        s = s.replaceAll("\\b\\d{1,2}:\\d{2}\\b", "#clock");

        // Prozentwerte oder reine Geschwindigkeits-/Statuszahlen mit Einheit.
        s = s.replaceAll("\\b\\d+(?:[.,]\\d+)?\\s*(?:km/h|mph|%)\\b", "#dynamic");

        return s.replaceAll("\\s+", " ").trim();
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

    private void publish(String body) {
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_navigation)
                .setContentTitle("Google Maps")
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .build();

        getSystemService(NotificationManager.class)
                .notify(BRIDGE_NOTIFICATION_ID, notification);
    }
}
