package com.vymo.bandviz.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssignmentRequest {

    @NotNull
    private Long developerId;

    @NotNull
    private Long projectId;

    @NotNull
    @Min(0) @Max(100)
    private Integer allocationPct;

    @NotNull
    private LocalDate startDate;

    // null = open-ended
    private LocalDate endDate;
}
