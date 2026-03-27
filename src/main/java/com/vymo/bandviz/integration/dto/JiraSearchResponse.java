package com.vymo.bandviz.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponse {
    private boolean isLast;
    private String nextPageToken;
    private List<JiraIssueResponse> issues;
}
