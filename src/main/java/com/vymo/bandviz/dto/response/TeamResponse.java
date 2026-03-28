package com.vymo.bandviz.dto.response;

import lombok.Data;

@Data
public class TeamResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private Integer projectCount;
    private Integer developerCount;
}
