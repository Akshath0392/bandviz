package com.vymo.bandviz.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SprintResponse {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
    private Long totalWorkingDays;
}
