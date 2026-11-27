package com.hackathon.visitors.dto;

import java.time.LocalDate;
import java.util.List;

/** Today's numbers, the next week's invitations and the last seven days. */
public record DailyStats(LocalDate date, long expectedToday, long notYetArrived, long insideNow, long checkedOutToday,
                         long cancelledToday, long upcomingNextSevenDays, long noShowsYesterday, double averageVisitMinutes,
                         List<DayCount> lastSevenDays, List<HostCount> busiestHostsToday) {
}
