package com.vymo.bandviz.dto.response;

import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import lombok.Data;

@Data
public class ProjectResponse {
    private Long id;
    private String name;
    private String jiraProjectKey;
    private String color;
    private Integer targetUtilizationPct;
    private ProjectDeliveryMode deliveryMode;
    private Boolean active;
}
