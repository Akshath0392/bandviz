package com.vymo.bandviz.dto.response;

import com.vymo.bandviz.domain.enums.LeaveStatus;
import com.vymo.bandviz.domain.enums.LeaveType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveResponse {
    private Long id;
    private Long developerId;
    private String developerName;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LeaveStatus status;
    private String notes;
    private Long durationDays;
}
