package com.tom.mibandmaps;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Event-driven filter. Distances always refer to a maneuver, never route totals. */
final class NavigationGate {
    static final int APPROACH_METERS = 100;
    static final int NOW_METERS = 20;
    private static final long REMOVAL_GRACE_MS = 5_000;
    private static final long STALE_SESSION_MS = 2 * 60 * 60 * 1_000L;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern DISTANCE = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(km|m|ft|mi|kilometers?|kilometres?|kilometer|meters?|metres?|meter)\\b", FLAGS);
    private static final Pattern TIME = Pattern.compile(
            "\\b\\d+(?:[.,]\\d+)?\\s*(min(?:ute[ns]?)?|mins|h|hrs?|hours?|std|stunden?)\\b", FLAGS);
    private static final Pattern ETA = Pattern.compile(
            "\\b(?:ankunft|arrival|eta)\\b|\\b\\d{1,2}:\\d{2}\\b", FLAGS);
    private static final Pattern ARRIVAL = Pattern.compile(
            "\\b(?:ziel erreicht|(?:sie haben|du hast) (?:ihr|dein|das) ziel erreicht|"
                    + "(?:am ziel|an deinem ziel|an ihrem ziel) angekommen|"
                    + "you(?: have|'ve) arrived|destination reached|arrived at (?:your|the) destination)\\b"
                    + "|^(?:angekommen|du bist angekommen|sie sind angekommen|arrived)[.!]?$", FLAGS);
    private static final Pattern TURN = Pattern.compile(
            "\\b(?:rechts|links|abbiegen|wenden|kreisverkehr|ausfahrt|auffahrt|"
                    + "turn|left|right|roundabout|exit|merge|u-turn)\\b", FLAGS);
    private static final Pattern STRAIGHT = Pattern.compile(
            "\\b(?:geradeaus|weitergehen|weiterfahren|weiter auf|continue|head|"
                    + "nach (?:norden|süden|osten|westen|nordosten|nordwesten|südosten|südwesten))\\b", FLAGS);
    private static final Pattern NOW = Pattern.compile("^(?:jetzt|sofort|now)\\b", FLAGS);

    // Persisted only when an alert is emitted or the Maps notification is removed.
    boolean started;
    boolean arrived;
    String maneuverKey = "";
    int stage;
    double minimumDistance = Double.NaN;
    long lastSeenAt;
    long removedAt;

    enum Phase { START, APPROACH, NOW, ARRIVAL }

    static final class Alert {
        final Phase phase;
        final String instruction;

        Alert(Phase phase, String instruction) {
            this.phase = phase;
            this.instruction = instruction;
        }

        String message(String initialBody) {
            switch (phase) {
                case START: return initialBody;
                case APPROACH: return "Bald: " + instruction;
                case NOW: return "Jetzt: " + instruction;
                default: return "Ziel erreicht.";
            }
        }
    }

    Alert accept(List<String> parts, long now) {
        Cue cue = parse(parts);
        if (cue == null) return null;

        // No timers: session cleanup runs only on the next notification event.
        if ((removedAt > 0 && now - removedAt >= REMOVAL_GRACE_MS)
                || (lastSeenAt > 0 && now - lastSeenAt > STALE_SESSION_MS)) {
            reset();
        }
        removedAt = 0;
        lastSeenAt = now;

        if (cue.arrival) {
            if (arrived) return null;
            started = true;
            arrived = true;
            return new Alert(Phase.ARRIVAL, "");
        }
        if (arrived) return null;

        int desiredStage = cue.turn
                ? (cue.immediate || (!Double.isNaN(cue.distance) && cue.distance <= NOW_METERS) ? 2
                : (!Double.isNaN(cue.distance) && cue.distance <= APPROACH_METERS ? 1 : 0)) : 0;

        boolean first = !started;
        started = true;
        if (cue.turn) {
            boolean same = sameManeuver(maneuverKey, cue.key);
            // Consecutive identical turns: a substantial distance increase after
            // the immediate alert indicates the next maneuver. Small jitter does not.
            boolean nextIdentical = same && stage == 2 && !Double.isNaN(minimumDistance)
                    && !Double.isNaN(cue.distance) && cue.distance > NOW_METERS
                    && cue.distance >= minimumDistance + 40;
            if (!same || nextIdentical) {
                maneuverKey = cue.key;
                stage = 0;
                minimumDistance = Double.NaN;
            }
            if (!Double.isNaN(cue.distance)) {
                minimumDistance = Double.isNaN(minimumDistance)
                        ? cue.distance : Math.min(minimumDistance, cue.distance);
            }
            if (first || desiredStage > stage) {
                stage = Math.max(stage, desiredStage);
                if (first) return new Alert(Phase.START, cue.text);
                return new Alert(stage == 2 ? Phase.NOW : Phase.APPROACH,
                        stage == 2 ? command(cue.text) : withDistance(cue));
            }
        } else if (!cue.key.isEmpty()) {
            // A real straight/heading step separates otherwise identical turns.
            maneuverKey = "";
            stage = 0;
            minimumDistance = Double.NaN;
        }
        return first ? new Alert(Phase.START, cue.text) : null;
    }

    void removed(long now) {
        removedAt = now;
    }

    void reset() {
        started = false;
        arrived = false;
        maneuverKey = "";
        stage = 0;
        minimumDistance = Double.NaN;
        lastSeenAt = 0;
        removedAt = 0;
    }

    static boolean isArrival(List<String> parts) {
        for (String part : parts) if (ARRIVAL.matcher(part).find()) return true;
        return false;
    }

    private static Cue parse(List<String> parts) {
        Cue fallback = null;
        Cue turn = null;
        double separateDistance = Double.NaN;
        for (String part : parts) {
            if (ARRIVAL.matcher(part).find()) return new Cue(part, "", false, true, false, Double.NaN);
            // Maps mixes the current instruction with duration and total distance.
            // Cut off those summaries before extracting a maneuver distance/key.
            for (String segment : part.split("\\s+[—–]\\s+|\\s*[·•]\\s*|\\n")) {
                String text = segment.trim();
                Matcher time = TIME.matcher(text);
                Matcher eta = ETA.matcher(text);
                int end = text.length();
                if (time.find()) end = Math.min(end, time.start());
                if (eta.find()) end = Math.min(end, eta.start());
                text = text.substring(0, end).trim();
                if (end < segment.trim().length()) {
                    if (fallback == null) fallback = new Cue("", "", false, false, false, Double.NaN);
                    // A route-summary boundary invalidates a preceding standalone distance.
                    separateDistance = Double.NaN;
                }
                if (text.isEmpty()) {
                    if (end < segment.trim().length()) break;
                    continue;
                }
                boolean isTurn = TURN.matcher(text).find();
                boolean isStraight = STRAIGHT.matcher(text).find();
                double distance = distance(text);
                if (isTurn && turn == null) {
                    if (Double.isNaN(distance)) distance = separateDistance;
                    turn = new Cue(text, key(text), true, false, NOW.matcher(text).find(), distance);
                } else if (isStraight && (fallback == null || fallback.key.isEmpty())) {
                    fallback = new Cue(text, key(text), false, false, false, Double.NaN);
                } else if (!Double.isNaN(distance) && isOnlyDistance(text)) {
                    separateDistance = distance;
                    // Some Maps versions put the distance in EXTRA_TITLE after the instruction.
                    if (turn != null && Double.isNaN(turn.distance)) turn.distance = distance;
                }
                if (end < segment.trim().length()) break;
            }
        }
        return turn != null ? turn : fallback;
    }

    private static boolean isOnlyDistance(String text) {
        return DISTANCE.matcher(text).replaceAll("").replaceAll("(?i)\\b(?:in|nach|noch)\\b", "")
                .replaceAll("[\\s:.,]+", "").isEmpty();
    }

    private static double distance(String text) {
        Matcher m = DISTANCE.matcher(text);
        if (!m.find()) return Double.NaN;
        double value = Double.parseDouble(m.group(1).replace(',', '.'));
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        if (unit.startsWith("k")) value *= 1_000;
        else if (unit.equals("ft")) value *= 0.3048;
        else if (unit.equals("mi")) value *= 1_609.344;
        return value;
    }

    private static String command(String text) {
        String s = DISTANCE.matcher(text).replaceAll("");
        return s.replaceFirst("(?iu)^(?:(?:in|nach|noch|jetzt|sofort|now)\\b[\\s:,]*)+", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static String key(String text) {
        String s = command(text).toLowerCase(Locale.ROOT);
        // Keep road names and exit numbers; discard common imperative wording.
        s = s.replaceAll("\\b(?:abbiegen|biegen|sie|bitte|ab|turn)\\b", "");
        return s.replaceAll("[\\s,;:]+", " ").trim();
    }

    private static boolean sameManeuver(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        // The final instruction sometimes omits the road name.
        return a.equals(b) || a.startsWith(b + " ") || b.startsWith(a + " ");
    }

    private static String withDistance(Cue cue) {
        if (DISTANCE.matcher(cue.text).find() || Double.isNaN(cue.distance)) return cue.text;
        return "In " + Math.round(cue.distance) + " m " + cue.text;
    }

    private static final class Cue {
        final String text;
        final String key;
        final boolean turn;
        final boolean arrival;
        final boolean immediate;
        double distance;

        Cue(String text, String key, boolean turn, boolean arrival, boolean immediate, double distance) {
            this.text = text;
            this.key = key;
            this.turn = turn;
            this.arrival = arrival;
            this.immediate = immediate;
            this.distance = distance;
        }
    }
}
