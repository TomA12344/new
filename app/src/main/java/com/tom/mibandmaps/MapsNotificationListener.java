package com.tom.mibandmaps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MapsNotificationListener extends NotificationListenerService {
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String CHANNEL_ID = "maps_navigation_bridge";
    private static final int BRIDGE_NOTIFICATION_ID = 9001;
    private static final String PREFS = "diag";
    private static final String GATE_PREFS = "navigation_gate_v1";

    private NavigationGate gate;
    private String activeMapsKey = "";
    private long lastDiagnosticAt;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        loadGate();
        createChannel();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("connected_at", System.currentTimeMillis()).apply();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            boolean foundNavigation = false;
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn != null && MAPS_PACKAGE.equals(sbn.getPackageName())) {
                        foundNavigation |= handleMapsNotification(sbn);
                    }
                }
            }
            if (!foundNavigation && gate.started && gate.removedAt == 0) {
                gate.removed(System.currentTimeMillis());
                saveGate();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (gate != null) saveGate();
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAPS_PACKAGE.equals(sbn.getPackageName())) return;
        handleMapsNotification(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !MAPS_PACKAGE.equals(sbn.getPackageName())) return;
        loadGate();
        if (sbn.getKey().equals(activeMapsKey)) {
            gate.removed(System.currentTimeMillis());
            saveGate();
        }
    }

    private boolean handleMapsNotification(StatusBarNotification sbn) {
        Notification source = sbn.getNotification();
        if (source == null) return false;
        loadGate();
        List<String> parts = extractNavigationParts(source);
        String body = body(parts);
        long now = System.currentTimeMillis();
        boolean eligible = (source.flags & Notification.FLAG_GROUP_SUMMARY) == 0
                && (sbn.isOngoing() || Notification.CATEGORY_NAVIGATION.equals(source.category)
                || NavigationGate.isArrival(parts));
        NavigationGate.Alert alert = eligible ? gate.accept(parts, now) : null;

        // Coalesce diagnostic writes during frequent Maps updates, without a timer.
        if (lastDiagnosticAt == 0 || now - lastDiagnosticAt >= 15_000 || alert != null) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("maps_seen_at", now)
                    .putString("maps_text", body.isEmpty()
                            ? "(Google Maps erkannt, aber kein Text gefunden)" : body)
                    .apply();
            lastDiagnosticAt = now;
        }
        if (eligible) activeMapsKey = sbn.getKey();
        if (alert != null) {
            publish(alert.message(body));
            saveGate();
        }
        return eligible;
    }

    private void loadGate() {
        if (gate != null) return;
        gate = new NavigationGate();
        SharedPreferences p = getSharedPreferences(GATE_PREFS, MODE_PRIVATE);
        gate.started = p.getBoolean("started", false);
        gate.arrived = p.getBoolean("arrived", false);
        gate.maneuverKey = p.getString("maneuver_key", "");
        gate.stage = p.getInt("stage", 0);
        gate.minimumDistance = p.getFloat("minimum_distance", Float.NaN);
        gate.lastSeenAt = p.getLong("last_seen_at", 0);
        gate.removedAt = p.getLong("removed_at", 0);
        activeMapsKey = p.getString("source_key", "");
    }

    private void saveGate() {
        getSharedPreferences(GATE_PREFS, MODE_PRIVATE).edit()
                .putBoolean("started", gate.started)
                .putBoolean("arrived", gate.arrived)
                .putString("maneuver_key", gate.maneuverKey)
                .putInt("stage", gate.stage)
                .putFloat("minimum_distance", (float) gate.minimumDistance)
                .putLong("last_seen_at", gate.lastSeenAt)
                .putLong("removed_at", gate.removedAt)
                .putString("source_key", activeMapsKey)
                .apply();
    }

    private List<String> extractNavigationParts(Notification n) {
        Set<String> parts = new LinkedHashSet<>();
        Bundle extras = n.extras;
        if (extras != null) {
            add(parts, extras.getCharSequence(Notification.EXTRA_TITLE));
            add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
            add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) for (CharSequence line : lines) add(parts, line);

            // Retain custom Maps text fields, but exclude known summary metadata:
            // its distance is the remaining route length, not the next turn.
            for (String key : extras.keySet()) {
                if (key.equals(Notification.EXTRA_SUB_TEXT)
                        || key.equals(Notification.EXTRA_INFO_TEXT)
                        || key.equals(Notification.EXTRA_SUMMARY_TEXT)) continue;
                Object value;
                try {
                    value = extras.get(key);
                } catch (Exception ignored) {
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
        return new ArrayList<>(parts);
    }

    private void add(Set<String> parts, CharSequence value) {
        if (value == null) return;
        String s = value.toString().replaceAll("[\\p{Z}\\s]+", " ").trim();
        if (!s.isEmpty() && !s.equalsIgnoreCase("Google Maps") && !s.equalsIgnoreCase("Maps")) {
            parts.add(s);
        }
    }

    private String body(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 160) continue;
            if (out.length() > 0) out.append(" — ");
            out.append(part);
            if (out.length() > 220) break;
        }
        String result = out.toString();
        return result.length() > 240 ? result.substring(0, 240) : result;
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Google Maps auf Mi Band", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Start, Abbiegehinweise und Ankunft aus Google Maps");
        channel.setSound(null, null);
        channel.enableVibration(false);
        nm.createNotificationChannel(channel);
    }

    private void publish(String body) {
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_navigation)
                .setContentTitle("Mi Band Maps")
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .build();
        getSystemService(NotificationManager.class).notify(BRIDGE_NOTIFICATION_ID, notification);
    }
}
