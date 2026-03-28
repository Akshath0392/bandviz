package com.vymo.bandviz.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeamRequest {

    @NotBlank
    private String name;

    private String description;
}
