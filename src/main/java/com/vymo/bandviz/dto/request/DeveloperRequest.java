package com.vymo.bandviz.dto.request;

import com.vymo.bandviz.domain.enums.DeveloperRole;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DeveloperRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotNull
    private DeveloperRole role;

    @NotNull
    @Min(1) @Max(80)
    private Integer weeklyCapacityHours;

    private String jiraUsername;
}
