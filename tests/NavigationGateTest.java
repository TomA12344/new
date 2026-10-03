package com.tom.mibandmaps;

import java.util.Arrays;

/** Standalone behavioral tests; run with Java 17 in the APK workflow. */
public final class NavigationGateTest {
    private static int checks;
    private static long clock = 1_000_000;

    public static void main(String[] args) {
        screenshotUpdates();
        normalRoute();
        jitterAndWording();
        skippedThreshold();
        separateFieldsAndRouteTotals();
        repeatedTurns();
        sessionLifecycle();
        unitsAndArrival();
        System.out.println("PASS: " + checks + " navigation sequence checks");
    }

    private static void screenshotUpdates() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Norden", "15 min · 850 m · Ankunft ca. 22:22");
        expect(g, null, "Nach Norden", "14 min · 850 m · Ankunft ca. 22:18");
        for (int meters = 840; meters >= 0; meters -= 10) {
            expect(g, null, "Nach Norden", "14 min · " + meters + " m · Ankunft ca. 22:18");
        }
        expect(g, null, "Nach Norden — 1 h 2 min · 8,5 km · Ankunft ca. 23:18");
    }

    private static void normalRoute() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Norden — 15 min · 850 m · Ankunft ca. 22:22");
        for (int m = 250; m > 100; m -= 10) expect(g, null, "In " + m + " m rechts abbiegen auf Hauptstraße");
        NavigationGate.Alert near = expect(g, NavigationGate.Phase.APPROACH, "In 100 m rechts abbiegen auf Hauptstraße");
        check(near.message("").equals("Bald: In 100 m rechts abbiegen auf Hauptstraße"), "near text");
        for (int m = 90; m > 20; m -= 10) expect(g, null, "In " + m + " m rechts abbiegen auf Hauptstraße");
        NavigationGate.Alert now = expect(g, NavigationGate.Phase.NOW, "In 20 m rechts abbiegen auf Hauptstraße");
        check(now.message("").equals("Jetzt: rechts abbiegen auf Hauptstraße"), "now text without distance");
        expect(g, null, "In 10 m rechts abbiegen auf Hauptstraße");
        expect(g, null, "Jetzt rechts abbiegen auf Hauptstraße");
        expect(g, null, "In 300 m links abbiegen auf Parkstraße");
        expect(g, NavigationGate.Phase.APPROACH, "In 80 m links abbiegen auf Parkstraße");
        expect(g, NavigationGate.Phase.NOW, "Jetzt links abbiegen auf Parkstraße");
        expect(g, NavigationGate.Phase.ARRIVAL, "Du hast dein Ziel erreicht");
        expect(g, null, "Du hast dein Ziel erreicht", "Ankunft 22:20");
    }

    private static void jitterAndWording() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Norden");
        expect(g, NavigationGate.Phase.APPROACH, "In 100 m rechts abbiegen auf Hauptstraße");
        for (int m : new int[]{90, 110, 100, 95, 80}) expect(g, null, "In " + m + " m rechts abbiegen auf Hauptstraße");
        expect(g, NavigationGate.Phase.NOW, "In 20 m rechts abbiegen auf Hauptstraße");
        expect(g, null, "In 25 m rechts abbiegen auf Hauptstraße");
        expect(g, null, "Jetzt rechts abbiegen");
        expect(g, null, "Jetzt biegen Sie rechts ab");
        expect(g, null, "Jetzt rechts abbiegen auf Hauptstraße — 3 min · 400 m · Ankunft ca. 22:18");
    }

    private static void skippedThreshold() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Süden");
        expect(g, null, "In 200 m links abbiegen");
        expect(g, NavigationGate.Phase.NOW, "In 15 m links abbiegen");
        expect(g, null, "In 10 m links abbiegen");
        expect(g, null, "In 25 m links abbiegen");
        // A start already inside a warning zone is one message, not two duplicates.
        NavigationGate shortRoute = new NavigationGate();
        expect(shortRoute, NavigationGate.Phase.START, "In 50 m rechts abbiegen");
        expect(shortRoute, null, "In 40 m rechts abbiegen");
        expect(shortRoute, NavigationGate.Phase.NOW, "Jetzt rechts abbiegen");
    }

    private static void separateFieldsAndRouteTotals() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Westen");
        expect(g, null, "Rechts abbiegen", "15 min · 20 m · Ankunft ca. 22:18");
        expect(g, null, "Rechts abbiegen — 14 min · 10 m · Ankunft ca. 22:18");
        expect(g, NavigationGate.Phase.APPROACH, "100 m", "Rechts abbiegen auf A 100", "3 min · 20 m · Ankunft 22:18");
        expect(g, null, "Rechts abbiegen auf A 100", "90 m");
        expect(g, NavigationGate.Phase.NOW, "15 m", "Rechts abbiegen auf A 100");
        expect(g, null, "Ankunft ca. 22:18");
        expect(new NavigationGate(), null, "Bewerte deinen Besuch", "Neue Rezension");
    }

    private static void repeatedTurns() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Norden");
        expect(g, NavigationGate.Phase.APPROACH, "In 100 m rechts abbiegen");
        expect(g, NavigationGate.Phase.NOW, "In 10 m rechts abbiegen");
        expect(g, null, "In 300 m rechts abbiegen");
        expect(g, NavigationGate.Phase.APPROACH, "In 100 m rechts abbiegen");
        expect(g, NavigationGate.Phase.NOW, "In 10 m rechts abbiegen");
        expect(g, NavigationGate.Phase.APPROACH, "In 50 m rechts abbiegen");
        expect(g, NavigationGate.Phase.NOW, "Jetzt rechts abbiegen");
        expect(g, null, "Geradeaus auf Hauptstraße");
        expect(g, NavigationGate.Phase.APPROACH, "In 40 m rechts abbiegen");
        expect(g, NavigationGate.Phase.NOW, "Jetzt rechts abbiegen");
    }

    private static void sessionLifecycle() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Nach Norden");
        expect(g, NavigationGate.Phase.APPROACH, "In 80 m rechts abbiegen");
        // Equivalent restored persisted state must not repeat start/approach.
        NavigationGate restored = new NavigationGate();
        restored.started = g.started;
        restored.arrived = g.arrived;
        restored.maneuverKey = g.maneuverKey;
        restored.stage = g.stage;
        restored.minimumDistance = g.minimumDistance;
        restored.lastSeenAt = g.lastSeenAt;
        expect(restored, null, "In 70 m rechts abbiegen");
        restored.removed(clock);
        expect(restored, null, "In 60 m rechts abbiegen");
        expect(restored, NavigationGate.Phase.NOW, "Jetzt rechts abbiegen");
        expect(restored, NavigationGate.Phase.ARRIVAL, "Ziel erreicht");
        expect(restored, null, "Ziel erreicht");
        restored.removed(clock);
        clock += 6_000;
        expect(restored, NavigationGate.Phase.START, "Nach Norden");
    }

    private static void unitsAndArrival() {
        NavigationGate g = new NavigationGate();
        expect(g, NavigationGate.Phase.START, "Head north");
        expect(g, null, "In 1,2 km turn right onto Main Street");
        expect(g, null, "In 0.2 mi turn right onto Main Street");
        expect(g, NavigationGate.Phase.APPROACH, "In 300 ft turn right onto Main Street");
        expect(g, NavigationGate.Phase.NOW, "In 50 ft turn right onto Main Street");
        expect(g, null, "Now turn right onto Main Street");
        expect(g, NavigationGate.Phase.ARRIVAL, "You have arrived");
        expect(g, null, "You have arrived");
        expect(new NavigationGate(), NavigationGate.Phase.ARRIVAL, "Du bist angekommen");
        check(!NavigationGate.isArrival(Arrays.asList("15 min · Ankunft ca. 22:22")), "ETA is not arrival");
    }

    private static NavigationGate.Alert expect(NavigationGate gate, NavigationGate.Phase phase, String... parts) {
        clock += 100;
        NavigationGate.Alert alert = gate.accept(Arrays.asList(parts), clock);
        NavigationGate.Phase actual = alert == null ? null : alert.phase;
        check(actual == phase, "expected " + phase + " but got " + actual + " for " + Arrays.toString(parts));
        return alert;
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
