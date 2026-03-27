package com.vymo.bandviz.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AssignmentResponse {
    private Long id;
    private Long developerId;
    private String developerName;
    private Long projectId;
    private String projectName;
    private Integer allocationPct;
    private LocalDate startDate;
    private LocalDate endDate;
}
