package com.tom.mibandmaps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MapsNotificationListener extends NotificationListenerService {
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String CHANNEL_ID = "maps_navigation_bridge";
    private static final int BRIDGE_NOTIFICATION_ID = 9001;
    private static final long MIN_UPDATE_MS = 8000L;

    private String lastBody = "";
    private long lastSentAt = 0L;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        createChannel();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAPS_PACKAGE.equals(sbn.getPackageName())) return;

        Notification source = sbn.getNotification();
        if (source == null) return;

        boolean looksLikeNavigation =
                Notification.CATEGORY_NAVIGATION.equals(source.category) ||
                (source.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (!looksLikeNavigation) return;

        String body = extractNavigationText(source.extras);
        if (body.isEmpty()) return;

        long now = SystemClock.elapsedRealtime();
        if (body.equals(lastBody)) return;
        if (now - lastSentAt < MIN_UPDATE_MS && sameManeuver(lastBody, body)) return;

        lastBody = body;
        lastSentAt = now;
        publish(body);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && MAPS_PACKAGE.equals(sbn.getPackageName())) {
            getSystemService(NotificationManager.class).cancel(BRIDGE_NOTIFICATION_ID);
        }
    }

    private String extractNavigationText(Bundle extras) {
        if (extras == null) return "";

        String big = clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String text = clean(extras.getCharSequence(Notification.EXTRA_TEXT));
        String title = clean(extras.getCharSequence(Notification.EXTRA_TITLE));
        String sub = clean(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));

        String best = !big.isEmpty() ? big : text;
        if (best.isEmpty()) best = title;

        if (!title.isEmpty() && !best.contains(title) && title.length() < 80) {
            best = title + " — " + best;
        }

        if (!sub.isEmpty() && !best.contains(sub) && best.length() < 120) {
            best = best + " · " + sub;
        }

        return best.trim();
    }

    private String clean(CharSequence value) {
        if (value == null) return "";
        return value.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private boolean sameManeuver(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        String pattern =
                "\\b\\d+(?:[.,]\\d+)?\\s*(?:m|km|ft|mi|meter|meters|kilometer|kilometers)\\b";

        String aa = a.toLowerCase().replaceAll(pattern, "#dist");
        String bb = b.toLowerCase().replaceAll(pattern, "#dist");

        return aa.equals(bb);
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
