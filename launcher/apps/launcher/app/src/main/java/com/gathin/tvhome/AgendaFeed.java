package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Google Calendar card's own feed: the TV downloads the calendar's secret iCal address (kept in
 * ical.txt in the launcher's external files folder, never in the APK), expands the next week's events and
 * keeps them in agenda-ical.json in its private files, in the same shape the PC's agenda.json uses.
 */
final class AgendaFeed {

    /** How old the downloaded week may get before Home fetches it again. */
    static final long REFRESH_MS = 6L * 60 * 60 * 1000;
    /** After a failed download (TV offline, Google slow), try again this much later. */
    static final long RETRY_MS = 30L * 60 * 1000;
    private static final int DAYS = 7;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STEPS = 50000;

    private AgendaFeed() {
    }

    /** The secret iCal address, or null when none is set. */
    static String url(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) return null;
        File f = new File(dir, "ical.txt");
        if (!f.canRead()) return null;
        try {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return s.startsWith("https://") ? s : null;
        } catch (IOException e) {
            return null;
        }
    }

    static File cache(Context c) {
        return new File(c.getFilesDir(), "agenda-ical.json");
    }

    static boolean stale(Context c, long now) {
        File f = cache(c);
        return !f.exists() || now - f.lastModified() > REFRESH_MS;
    }

    /** Downloads, expands and saves the coming week. Runs off the main thread. */
    static void refresh(Context c, String url) throws Exception {
        String ics = download(url);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        ZonedDateTime from = today.atStartOfDay(zone);
        ZonedDateTime to = today.plusDays(DAYS + 1).atStartOfDay(zone);
        List<Item> items = expand(ics, from, to, zone);
        JSONArray events = new JSONArray();
        for (Item it : items) {
            JSONObject e = new JSONObject();
            e.put("title", it.title);
            e.put("allDay", it.allDay);
            e.put("start", it.allDay ? it.start.toLocalDate().toString() : it.start.toOffsetDateTime().toString());
            e.put("end", it.allDay ? it.end.toLocalDate().toString() : it.end.toOffsetDateTime().toString());
            events.put(e);
        }
        JSONObject root = new JSONObject();
        root.put("updated", Instant.now().toString());
        root.put("events", events);
        File out = cache(c);
        File tmp = new File(out.getPath() + ".tmp");
        Files.write(tmp.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(out)) {
            Files.write(out.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static String download(String address) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(address).openConnection();
        http.setConnectTimeout(15000);
        http.setReadTimeout(30000);
        http.setInstanceFollowRedirects(true);
        try {
            int code = http.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > MAX_BYTES) throw new IOException("calendar too large");
                }
                String text = buf.toString("UTF-8");
                if (!text.contains("BEGIN:VCALENDAR")) throw new IOException("not a calendar");
                return text;
            }
        } finally {
            http.disconnect();
        }
    }

    // ---- iCalendar -------------------------------------------------------------------------------

    static final class Item {
        final String title;
        final ZonedDateTime start, end;
        final boolean allDay;

        Item(String title, ZonedDateTime start, ZonedDateTime end, boolean allDay) {
            this.title = title;
            this.start = start;
            this.end = end;
            this.allDay = allDay;
        }
    }

    private static final class Time {
        final ZonedDateTime t;
        final boolean date;

        Time(ZonedDateTime t, boolean date) {
            this.t = t;
            this.date = date;
        }
    }

    private static final class Event {
        String uid = "", summary = "", rrule, status = "";
        Time start, end, recurrenceId;
        Duration duration;
        final Set<Instant> exdates = new HashSet<>();
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);

    private static Time time(String value, String tzid, ZoneId fallback) {
        try {
            value = value.trim();
            if (value.length() == 8) {
                return new Time(LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(fallback), true);
            }
            if (value.endsWith("Z")) {
                return new Time(LocalDateTime.parse(value.substring(0, value.length() - 1), STAMP).atZone(ZoneOffset.UTC), false);
            }
            ZoneId zone = fallback;
            if (tzid != null) {
                try {
                    zone = ZoneId.of(tzid);
                } catch (Exception unknown) {
                    zone = fallback;
                }
            }
            return new Time(LocalDateTime.parse(value, STAMP).atZone(zone), false);
        } catch (Exception bad) {
            return null;
        }
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' || n == 'N' ? ' ' : n);
            } else {
                b.append(ch);
            }
        }
        return b.toString().trim();
    }

    private static List<Event> parse(String ics, ZoneId zone) {
        String[] raw = ics.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> lines = new ArrayList<>();
        for (String l : raw) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && !lines.isEmpty()) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + l.substring(1));
            } else {
                lines.add(l);
            }
        }
        List<Event> events = new ArrayList<>();
        Event cur = null;
        int depth = 0;
        for (String line : lines) {
            if (line.equals("BEGIN:VEVENT")) {
                cur = new Event();
                depth = 0;
                continue;
            }
            if (cur == null) continue;
            if (line.startsWith("BEGIN:")) {
                depth++;
                continue;
            }
            if (line.startsWith("END:")) {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                if (line.equals("END:VEVENT")) {
                    if (cur.start != null) events.add(cur);
                    cur = null;
                }
                continue;
            }
            if (depth > 0) continue;
            int colon = -1;
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == '"') quoted = !quoted;
                else if (ch == ':' && !quoted) {
                    colon = i;
                    break;
                }
            }
            if (colon < 0) continue;
            String head = line.substring(0, colon), value = line.substring(colon + 1);
            String[] parts = head.split(";");
            String name = parts[0].toUpperCase(Locale.ROOT);
            String tzid = null;
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].toUpperCase(Locale.ROOT).startsWith("TZID=")) tzid = parts[i].substring(5).replace("\"", "");
            }
            switch (name) {
                case "UID": cur.uid = value.trim(); break;
                case "SUMMARY": cur.summary = unescape(value); break;
                case "STATUS": cur.status = value.trim().toUpperCase(Locale.ROOT); break;
                case "RRULE": cur.rrule = value.trim(); break;
                case "DTSTART": cur.start = time(value, tzid, zone); break;
                case "DTEND": cur.end = time(value, tzid, zone); break;
                case "RECURRENCE-ID": cur.recurrenceId = time(value, tzid, zone); break;
                case "DURATION":
                    try {
                        cur.duration = Duration.parse(value.trim());
                    } catch (Exception ignored) {
                        cur.duration = null;
                    }
                    break;
                case "EXDATE":
                    for (String v : value.split(",")) {
                        Time x = time(v, tzid, zone);
                        if (x != null) cur.exdates.add(x.t.toInstant());
                    }
                    break;
                default:
                    break;
            }
        }
        return events;
    }

    private static Duration length(Event e) {
        if (e.end != null) return Duration.between(e.start.t, e.end.t);
        if (e.duration != null) return e.duration;
        return e.start.date ? Duration.ofDays(1) : Duration.ZERO;
    }

    private static void add(List<Item> out, Event e, ZonedDateTime start, Duration len, ZonedDateTime from, ZonedDateTime to, ZoneId zone) {
        ZonedDateTime s = e.start.date ? start.toLocalDate().atStartOfDay(zone) : start.withZoneSameInstant(zone);
        ZonedDateTime en = e.start.date ? start.toLocalDate().plusDays(Math.max(1, len.toDays())).atStartOfDay(zone)
                : s.plus(len);
        if (!s.isBefore(to) || !en.isAfter(from)) return;
        out.add(new Item(e.summary.isEmpty() ? "(Không tên)" : e.summary, s, en, e.start.date));
    }

    static List<Item> expand(String ics, ZonedDateTime from, ZonedDateTime to, ZoneId zone) {
        List<Event> events = parse(ics, zone);
        Map<String, Event> overrides = new HashMap<>();
        for (Event e : events) {
            if (e.recurrenceId != null) overrides.put(e.uid + "|" + e.recurrenceId.t.toInstant(), e);
        }
        List<Item> out = new ArrayList<>();
        for (Event e : events) {
            if (e.recurrenceId != null) {
                if (!"CANCELLED".equals(e.status)) add(out, e, e.start.t, length(e), from, to, zone);
                continue;
            }
            if ("CANCELLED".equals(e.status)) continue;
            Duration len = length(e);
            if (e.rrule == null) {
                add(out, e, e.start.t, len, from, to, zone);
                continue;
            }
            Map<String, String> rule = new HashMap<>();
            for (String p : e.rrule.split(";")) {
                int eq = p.indexOf('=');
                if (eq > 0) rule.put(p.substring(0, eq).toUpperCase(Locale.ROOT), p.substring(eq + 1));
            }
            String freq = rule.getOrDefault("FREQ", "");
            int interval = 1;
            try {
                interval = Math.max(1, Integer.parseInt(rule.getOrDefault("INTERVAL", "1")));
            } catch (NumberFormatException ignored) {
                interval = 1;
            }
            int count = -1;
            try {
                if (rule.containsKey("COUNT")) count = Integer.parseInt(rule.get("COUNT"));
            } catch (NumberFormatException ignored) {
                count = -1;
            }
            ZonedDateTime until = null;
            if (rule.containsKey("UNTIL")) {
                Time u = time(rule.get("UNTIL"), null, e.start.t.getZone());
                if (u != null) until = u.date ? u.t.plusDays(1).minusNanos(1) : u.t;
            }
            List<DayOfWeek> byDay = new ArrayList<>();
            if (rule.containsKey("BYDAY")) {
                for (String d : rule.get("BYDAY").split(",")) {
                    String code = d.replaceAll("^[+-]?\\d+", "");
                    DayOfWeek w = day(code);
                    if (w != null && !byDay.contains(w)) byDay.add(w);
                }
                byDay.sort(null);
            }
            ZoneId z = e.start.t.getZone();
            LocalDateTime base = e.start.t.toLocalDateTime();
            int seen = 0;
            boolean done = false;
            for (int k = 0; k < MAX_STEPS && !done; k++) {
                List<LocalDateTime> candidates = new ArrayList<>();
                switch (freq) {
                    case "DAILY": candidates.add(base.plusDays((long) k * interval)); break;
                    case "WEEKLY":
                        if (byDay.isEmpty()) {
                            candidates.add(base.plusWeeks((long) k * interval));
                        } else {
                            LocalDate week = base.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    .plusWeeks((long) k * interval);
                            for (DayOfWeek w : byDay) {
                                LocalDateTime c = week.plusDays(w.getValue() - 1).atTime(base.toLocalTime());
                                if (!c.isBefore(base)) candidates.add(c);
                            }
                        }
                        break;
                    case "MONTHLY": {
                        LocalDateTime c = base.plusMonths((long) k * interval);
                        if (c.getDayOfMonth() == base.getDayOfMonth()) candidates.add(c);
                        break;
                    }
                    case "YEARLY": {
                        LocalDateTime c = base.plusYears((long) k * interval);
                        if (c.getDayOfMonth() == base.getDayOfMonth()) candidates.add(c);
                        break;
                    }
                    default:
                        candidates.add(base);
                        done = true;
                        break;
                }
                for (LocalDateTime c : candidates) {
                    ZonedDateTime cz = c.atZone(z);
                    if (until != null && cz.isAfter(until)) {
                        done = true;
                        break;
                    }
                    seen++;
                    if (count >= 0 && seen > count) {
                        done = true;
                        break;
                    }
                    if (!cz.isBefore(to)) {
                        done = true;
                        break;
                    }
                    Instant at = cz.toInstant();
                    if (e.exdates.contains(at) || overrides.containsKey(e.uid + "|" + at)) continue;
                    if (cz.plus(len).isAfter(from)) add(out, e, cz, len, from, to, zone);
                }
            }
        }
        out.sort((a, b) -> a.start.compareTo(b.start));
        return out;
    }

    private static DayOfWeek day(String code) {
        switch (code.toUpperCase(Locale.ROOT)) {
            case "MO": return DayOfWeek.MONDAY;
            case "TU": return DayOfWeek.TUESDAY;
            case "WE": return DayOfWeek.WEDNESDAY;
            case "TH": return DayOfWeek.THURSDAY;
            case "FR": return DayOfWeek.FRIDAY;
            case "SA": return DayOfWeek.SATURDAY;
            case "SU": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }
}
