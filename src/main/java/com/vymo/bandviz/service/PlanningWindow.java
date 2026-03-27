package com.vymo.bandviz.service;

import java.time.LocalDate;

public record PlanningWindow(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String mode,
        boolean fallback
) {}
