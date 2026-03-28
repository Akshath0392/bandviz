package com.vymo.bandviz.dto.response;

import com.vymo.bandviz.domain.enums.TicketPriority;
import com.vymo.bandviz.domain.enums.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JiraTicketResponse {
    private Long id;
    private String ticketKey;
    private String summary;
    private String assigneeJiraUsername;
    private Integer storyPoints;
    private TicketStatus status;
    private String rawStatus;
    private TicketPriority priority;
    private String projectKey;
    private String projectName;
    private Long teamId;
    private String teamName;
    private String sprintName;
    private String ticketUrl;
    private LocalDateTime lastSyncedAt;
}
