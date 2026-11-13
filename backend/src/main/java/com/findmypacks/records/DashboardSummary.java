package com.findmypacks.records;

import java.util.List;

public record DashboardSummary(
        List<DashboardCount> byStatus,
        List<DashboardCount> byCarrier,
        List<DashboardDayCount> postedPerDay) {
}
