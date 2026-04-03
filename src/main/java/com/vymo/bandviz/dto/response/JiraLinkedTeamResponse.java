package com.vymo.bandviz.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class JiraLinkedTeamResponse {
    private Long teamId;
    private String teamName;
    private List<ProjectLink> projects;

    @Data
    public static class ProjectLink {
        private Long projectId;
        private String projectName;
        private String jiraProjectKey;
    }
}
