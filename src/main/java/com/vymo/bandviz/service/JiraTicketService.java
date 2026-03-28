package com.vymo.bandviz.service;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.dto.response.JiraTicketResponse;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.JiraTicketRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import com.vymo.bandviz.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JiraTicketService {

    private final DeveloperRepository developerRepository;
    private final JiraTicketRepository jiraTicketRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final JiraProperties jiraProperties;

    public List<JiraTicketResponse> findTickets(Long developerId, Long teamId, Long projectId) {
        List<JiraTicket> tickets = resolveTickets(developerId, teamId, projectId);
        Map<String, Project> projectsByKey = projectRepository.findAll().stream()
                .filter(project -> project.getJiraProjectKey() != null && !project.getJiraProjectKey().isBlank())
                .collect(Collectors.toMap(Project::getJiraProjectKey, Function.identity(), (left, right) -> left));

        return tickets.stream()
                .sorted(Comparator
                        .comparing(JiraTicket::getLastSyncedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(JiraTicket::getTicketKey))
                .map(ticket -> toResponse(ticket, projectsByKey.get(ticket.getProjectKey())))
                .toList();
    }

    private List<JiraTicket> resolveTickets(Long developerId, Long teamId, Long projectId) {
        if (developerId != null) {
            return developerRepository.findById(developerId)
                    .map(Developer::getJiraUsername)
                    .filter(jiraUsername -> jiraUsername != null && !jiraUsername.isBlank())
                    .map(jiraTicketRepository::findAllByAssigneeJiraUsername)
                    .orElse(List.of());
        }

        if (projectId != null) {
            return projectRepository.findById(projectId)
                    .map(Project::getJiraProjectKey)
                    .filter(projectKey -> projectKey != null && !projectKey.isBlank())
                    .map(jiraTicketRepository::findAllByProjectKey)
                    .orElse(List.of());
        }

        if (teamId != null) {
            teamRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
            List<String> projectKeys = projectRepository.findAllByActiveTrueAndTeamId(teamId).stream()
                    .map(Project::getJiraProjectKey)
                    .filter(Objects::nonNull)
                    .filter(key -> !key.isBlank())
                    .toList();
            List<String> jiraUsernames = developerRepository.findAllByActiveTrueAndTeamId(teamId).stream()
                    .map(Developer::getJiraUsername)
                    .filter(Objects::nonNull)
                    .filter(username -> !username.isBlank())
                    .toList();

            Map<Long, JiraTicket> ticketsById = new LinkedHashMap<>();
            if (!projectKeys.isEmpty()) {
                jiraTicketRepository.findAllByProjectKeyIn(projectKeys)
                        .forEach(ticket -> ticketsById.put(ticket.getId(), ticket));
            }
            if (!jiraUsernames.isEmpty()) {
                jiraTicketRepository.findAllByAssigneeJiraUsernameIn(jiraUsernames)
                        .forEach(ticket -> ticketsById.put(ticket.getId(), ticket));
            }
            return new ArrayList<>(ticketsById.values());
        }

        return new ArrayList<>();
    }

    private JiraTicketResponse toResponse(JiraTicket ticket, Project project) {
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
                .projectName(project != null ? project.getName() : resolveProjectName(ticket.getProjectKey()))
                .teamId(project != null && project.getTeam() != null ? project.getTeam().getId() : null)
                .teamName(project != null && project.getTeam() != null ? project.getTeam().getName() : null)
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
