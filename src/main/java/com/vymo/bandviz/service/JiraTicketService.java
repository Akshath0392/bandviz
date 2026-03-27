package com.vymo.bandviz.service;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.dto.response.JiraTicketResponse;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.JiraTicketRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JiraTicketService {

    private final DeveloperRepository developerRepository;
    private final JiraTicketRepository jiraTicketRepository;
    private final ProjectRepository projectRepository;
    private final JiraProperties jiraProperties;

    public List<JiraTicketResponse> findByDeveloper(Long developerId) {
        return developerRepository.findById(developerId)
                .map(developer -> developer.getJiraUsername())
                .filter(jiraUsername -> jiraUsername != null && !jiraUsername.isBlank())
                .map(jiraUsername -> jiraTicketRepository.findAllByAssigneeJiraUsername(jiraUsername).stream()
                        .sorted(Comparator
                                .comparing(JiraTicket::getLastSyncedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(JiraTicket::getTicketKey))
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    private JiraTicketResponse toResponse(JiraTicket ticket) {
        return JiraTicketResponse.builder()
                .id(ticket.getId())
                .ticketKey(ticket.getTicketKey())
                .summary(ticket.getSummary())
                .assigneeJiraUsername(ticket.getAssigneeJiraUsername())
                .storyPoints(ticket.getStoryPoints())
                .status(ticket.getStatus())
                .rawStatus(ticket.getRawStatus())
                .priority(ticket.getPriority())
                .projectKey(ticket.getProjectKey())
                .projectName(resolveProjectName(ticket.getProjectKey()))
                .sprintName(ticket.getSprintName())
                .ticketUrl(resolveTicketUrl(ticket.getTicketKey()))
                .lastSyncedAt(ticket.getLastSyncedAt())
                .build();
    }

    private String resolveProjectName(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return null;
        }
        return projectRepository.findAllByActiveTrue().stream()
                .filter(project -> projectKey.equals(project.getJiraProjectKey()))
                .map(project -> project.getName())
                .findFirst()
                .orElse(projectKey);
    }

    private String resolveTicketUrl(String ticketKey) {
        if (ticketKey == null || ticketKey.isBlank()) {
            return null;
        }
        String baseUrl = jiraProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.replaceAll("/$", "") + "/browse/" + ticketKey;
    }
}
