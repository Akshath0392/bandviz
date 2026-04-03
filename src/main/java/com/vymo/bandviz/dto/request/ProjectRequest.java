package com.vymo.bandviz.dto.request;

import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    private String jiraProjectKey;

    private String color;

    @Min(0) @Max(100)
    private Integer targetUtilizationPct = 70;

    private ProjectDeliveryMode deliveryMode = ProjectDeliveryMode.HYBRID;

    @NotNull
    private Long teamId;

    private List<Long> permittedTeamIds;
}
