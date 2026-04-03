package com.vymo.bandviz.dto.response;

import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import lombok.Data;

import java.util.List;

@Data
public class ProjectResponse {
    private Long id;
    private String name;
    private String jiraProjectKey;
    private String color;
    private Integer targetUtilizationPct;
    private ProjectDeliveryMode deliveryMode;
    private Long teamId;
    private String teamName;
    private List<Long> permittedTeamIds;
    private List<String> permittedTeamNames;
    private Boolean active;
}
