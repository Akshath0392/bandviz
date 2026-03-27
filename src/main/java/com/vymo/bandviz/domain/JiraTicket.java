package com.vymo.bandviz.domain;

import com.vymo.bandviz.domain.enums.TicketPriority;
import com.vymo.bandviz.domain.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "jira_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticketKey;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // Jira username of the assignee — matched to Developer.jiraUsername
    private String assigneeJiraUsername;

    private Integer storyPoints;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private String rawStatus;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority;

    private String projectKey;

    private String sprintName;

    private LocalDateTime lastSyncedAt;
}
