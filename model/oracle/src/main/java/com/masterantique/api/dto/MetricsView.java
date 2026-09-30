package com.masterantique.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Action 25: the 7-day summary. {@code hasData} is false (every other field null) when no ticket is completed. */
public record MetricsView(boolean hasData, LocalDate windowStart, LocalDate windowEnd, List<LocalDate> days,
                          List<EmployeeSeries> employeeSeries, Integer periodTotal, Double averageCompletedPerDay,
                          Integer createdInPeriod, Double averageCreatedPerDay, Double completedToCreatedRatio,
                          DayCount mostProductiveDay, Integer daysWithNoCompletions, NameCount busiestEmployee,
                          CommentMetrics comments, TicketCount mostCommentedTicket) {

    public static MetricsView noData() {
        return new MetricsView(false, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
