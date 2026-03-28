package com.vymo.bandviz.dto.response;

import com.vymo.bandviz.domain.enums.DeveloperRole;
import lombok.Data;

@Data
public class DeveloperResponse {
    private Long id;
    private String name;
    private String email;
    private DeveloperRole role;
    private Integer weeklyCapacityHours;
    private String jiraUsername;
    private Long teamId;
    private String teamName;
    private Boolean active;
}
