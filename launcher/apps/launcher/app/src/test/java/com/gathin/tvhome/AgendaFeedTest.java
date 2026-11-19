package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class AgendaFeedTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String ICS = String.join("\r\n",
            "BEGIN:VCALENDAR",
            "BEGIN:VTIMEZONE", "TZID:Asia/Ho_Chi_Minh", "BEGIN:STANDARD", "DTSTART:19700101T000000",
            "TZOFFSETFROM:+0700", "TZOFFSETTO:+0700", "END:STANDARD", "END:VTIMEZONE",
            // Every day 04:30, from 1 Nov, one day skipped, one day moved to 05:00.
            "BEGIN:VEVENT", "UID:a@x", "DTSTART;TZID=Asia/Ho_Chi_Minh:20261101T043000",
            "DTEND;TZID=Asia/Ho_Chi_Minh:20261101T053000", "RRULE:FREQ=DAILY",
            "EXDATE;TZID=Asia/Ho_Chi_Minh:20261121T043000", "SUMMARY:Tập sáng", "END:VEVENT",
            "BEGIN:VEVENT", "UID:a@x", "RECURRENCE-ID;TZID=Asia/Ho_Chi_Minh:20261122T043000",
            "DTSTART;TZID=Asia/Ho_Chi_Minh:20261122T050000", "DTEND;TZID=Asia/Ho_Chi_Minh:20261122T060000",
            "SUMMARY:Tập sáng muộn", "END:VEVENT",
            // Weekly on Mon and Wed, UTC times, ending after 2 Dec.
            "BEGIN:VEVENT", "UID:b@x", "DTSTART:20261102T013000Z", "DTEND:20261102T023000Z",
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261125T000000Z", "SUMMARY:Họp\\, nhóm", "END:VEVENT",
            // All-day, one off.
            "BEGIN:VEVENT", "UID:c@x", "DTSTART;VALUE=DATE:20261124", "DTEND;VALUE=DATE:20261125",
            "SUMMARY:Sinh nhật", "END:VEVENT",
            "BEGIN:VEVENT", "UID:d@x", "DTSTART:20261123T010000Z", "STATUS:CANCELLED", "SUMMARY:Huỷ", "END:VEVENT",
            "END:VCALENDAR", "");

    @Test
    public void expandsTheComingWeek() {
        ZonedDateTime from = LocalDate.of(2026, 11, 19).atStartOfDay(VN);
        ZonedDateTime to = from.plusDays(8);
        List<AgendaFeed.Item> items = AgendaFeed.expand(ICS, from, to, VN);
        long daily = items.stream().filter(i -> i.title.equals("Tập sáng")).count();
        // 19..26 Nov is 8 days, minus the skipped 21st and the moved 22nd.
        assertEquals(6, daily);
        AgendaFeed.Item moved = items.stream().filter(i -> i.title.equals("Tập sáng muộn")).findFirst().get();
        assertEquals(5, moved.start.getHour());
        long meetings = items.stream().filter(i -> i.title.equals("Họp, nhóm")).count();
        // Mon 23 and Wed 25... Wed 25 08:30 is after UNTIL (25 Nov 00:00Z), so only Mon 23.
        assertEquals(1, meetings);
        AgendaFeed.Item meeting = items.stream().filter(i -> i.title.equals("Họp, nhóm")).findFirst().get();
        assertEquals(8, meeting.start.getHour());
        AgendaFeed.Item birthday = items.stream().filter(i -> i.allDay).findFirst().get();
        assertEquals(24, birthday.start.getDayOfMonth());
        assertEquals(0, items.stream().filter(i -> i.title.equals("Huỷ")).count());
        for (int i = 1; i < items.size(); i++) {
            assertEquals(true, !items.get(i).start.isBefore(items.get(i - 1).start));
        }
    }
}
