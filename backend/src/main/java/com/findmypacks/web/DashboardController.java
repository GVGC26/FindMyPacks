package com.findmypacks.web;

import com.findmypacks.records.DashboardSummary;
import com.findmypacks.records.PackageDataAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/dashboard")
public class DashboardController {
    private final PackageDataAccess packages;

    public DashboardController(PackageDataAccess packages) {
        this.packages = packages;
    }

    @GetMapping
    public DashboardSummary dashboard() {
        return packages.dashboardSummaryForLastSevenDays();
    }
}
