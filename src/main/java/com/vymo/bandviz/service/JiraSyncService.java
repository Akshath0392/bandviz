package com.vymo.bandviz.service;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.enums.TicketPriority;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.integration.JiraClient;
import com.vymo.bandviz.integration.dto.JiraIssueResponse;
import com.vymo.bandviz.integration.dto.JiraSearchResponse;
import com.vymo.bandviz.repository.JiraTicketRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraSyncService {

    private final JiraClient jiraClient;
    private final JiraTicketRepository jiraTicketRepository;
    private final ProjectRepository projectRepository;
    private final JiraProperties jiraProperties;

    private volatile LocalDateTime lastSyncedAt;
    private volatile String lastSyncStatus = "Never synced";

    @Transactional
    @Scheduled(fixedDelayString = "${bandviz.jira.sync-interval-ms:900000}") // default 15 min
    public SyncResult syncAll() {
        if (!jiraProperties.isSyncEnabled()) {
            log.debug("Jira sync is disabled. Skipping.");
            return new SyncResult(0, 0, "Sync disabled");
        }

        log.info("Starting Jira sync...");
        List<String> projectKeys = resolveProjectKeys();

        int totalSynced = 0;
        int errors = 0;

        for (String projectKey : projectKeys) {
            try {
                log.info("Starting Jira sync for project key {}", projectKey);
                int synced = syncProject(projectKey);
                totalSynced += synced;
                log.info("Synced {} tickets for project {}", synced, projectKey);
            } catch (Exception e) {
                log.error("Failed to sync project {}: {}", projectKey, e.getMessage());
                errors++;
            }
        }

        lastSyncedAt = LocalDateTime.now();
        lastSyncStatus = String.format("OK · %d tickets synced · %d project errors", totalSynced, errors);
        log.info("Jira sync complete. {}", lastSyncStatus);
        return new SyncResult(totalSynced, errors, lastSyncStatus);
    }

    private List<String> resolveProjectKeys() {
        List<String> configuredKeys = jiraProperties.getProjectKeys().stream()
                .filter(key -> key != null && !key.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (!configuredKeys.isEmpty()) {
            log.info("Using Jira project keys from configuration: {}", configuredKeys);
            return configuredKeys;
        }

        List<String> projectKeys = projectRepository.findAllByActiveTrue().stream()
                .filter(p -> p.getJiraProjectKey() != null && !p.getJiraProjectKey().isBlank())
                .map(p -> p.getJiraProjectKey().trim())
                .distinct()
                .toList();

        log.info("Using Jira project keys from active projects: {}", projectKeys);
        return projectKeys;
    }

    private int syncProject(String projectKey) {
        String nextPageToken = null;
        int totalSynced = 0;
        int page = 1;
        LocalDateTime syncTime = LocalDateTime.now();
        Set<String> seenTicketKeys = new HashSet<>();

        while (true) {
            JiraSearchResponse response = jiraClient.fetchOpenIssues(projectKey, nextPageToken);
            if (response == null || response.getIssues() == null || response.getIssues().isEmpty()) {
                log.warn("No Jira issues returned for project {} on page {}", projectKey, page);
                break;
            }

            log.info("Processing {} Jira issues for project {} on page {}", response.getIssues().size(), projectKey, page);

            for (JiraIssueResponse issue : response.getIssues()) {
                upsertTicket(issue, projectKey, syncTime);
                seenTicketKeys.add(issue.getKey());
                totalSynced++;
            }

            if (response.isLast() || response.getNextPageToken() == null || response.getNextPageToken().isBlank()) {
                log.info("Reached last Jira page for project {}", projectKey);
                break;
            }
            nextPageToken = response.getNextPageToken();
            page++;
        }

        markResolvedTickets(projectKey, seenTicketKeys, syncTime);
        return totalSynced;
    }

    private void upsertTicket(JiraIssueResponse issue, String projectKey, LocalDateTime syncTime) {
        Optional<JiraTicket> existing = jiraTicketRepository.findByTicketKey(issue.getKey());
        JiraTicket ticket = existing.orElse(new JiraTicket());

        ticket.setTicketKey(issue.getKey());
        ticket.setProjectKey(projectKey);
        ticket.setLastSyncedAt(syncTime);

        if (issue.getFields() != null) {
            ticket.setSummary(issue.getFields().getSummary());
            ticket.setStatus(mapStatus(issue.getFields().getStatus()));
            ticket.setRawStatus(issue.getFields().getStatus() != null ? issue.getFields().getStatus().getName() : null);
            ticket.setPriority(mapPriority(issue.getFields().getPriority()));
            ticket.setStoryPoints(issue.getFields().getCustomfield_10016());

            if (issue.getFields().getAssignee() != null) {
                ticket.setAssigneeJiraUsername(issue.getFields().getAssignee().getEmailAddress());
            }

            // Use the active sprint name if available
            List<JiraIssueResponse.Fields.Sprint> sprints = issue.getFields().getCustomfield_10020();
            if (sprints != null) {
                sprints.stream()
                        .filter(s -> "active".equalsIgnoreCase(s.getState()))
                        .findFirst()
                        .ifPresent(s -> ticket.setSprintName(s.getName()));
            }
        }

        jiraTicketRepository.save(ticket);
    }

    private void markResolvedTickets(String projectKey, Set<String> seenTicketKeys, LocalDateTime syncTime) {
        jiraTicketRepository.findAllByProjectKey(projectKey).stream()
                .filter(ticket -> ticket.getStatus() != TicketStatus.DONE)
                .filter(ticket -> !seenTicketKeys.contains(ticket.getTicketKey()))
                .forEach(ticket -> {
                    ticket.setStatus(TicketStatus.DONE);
                    ticket.setRawStatus("Done");
                    ticket.setLastSyncedAt(syncTime);
                    jiraTicketRepository.save(ticket);
                });
    }

    public SyncStatusResponse getStatus() {
        return new SyncStatusResponse(
                jiraProperties.isSyncEnabled(),
                lastSyncedAt,
                lastSyncStatus,
                jiraTicketRepository.count(),
                jiraProperties.getBaseUrl(),
                resolveProjectKeys()
        );
    }

    public List<ProjectSyncStatusResponse> getProjectMappings() {
        return projectRepository.findAllByActiveTrue().stream()
                .filter(project -> project.getJiraProjectKey() != null && !project.getJiraProjectKey().isBlank())
                .map(project -> {
                    String projectKey = project.getJiraProjectKey().trim();
                    List<JiraTicket> tickets = jiraTicketRepository.findAllByProjectKey(projectKey);
                    LocalDateTime latestSync = tickets.stream()
                            .map(JiraTicket::getLastSyncedAt)
                            .filter(value -> value != null)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    long openTickets = jiraTicketRepository.countByProjectKeyAndStatusNot(projectKey, TicketStatus.DONE);
                    String syncState = latestSync == null
                            ? "NOT_SYNCED"
                            : latestSync.isBefore(LocalDateTime.now().minusMinutes(30))
                                ? "STALE"
                                : "SYNCED";
                    return new ProjectSyncStatusResponse(
                            project.getName(),
                            projectKey,
                            openTickets,
                            latestSync,
                            syncState
                    );
                })
                .toList();
    }

    private TicketStatus mapStatus(JiraIssueResponse.Fields.Status status) {
        if (status == null) return TicketStatus.TO_DO;
        return switch (status.getName().toLowerCase()) {
            case "in progress"  -> TicketStatus.IN_PROGRESS;
            case "in review", "code review" -> TicketStatus.IN_REVIEW;
            case "blocked"      -> TicketStatus.BLOCKED;
            case "done", "closed", "resolved" -> TicketStatus.DONE;
            default             -> TicketStatus.TO_DO;
        };
    }

    private TicketPriority mapPriority(JiraIssueResponse.Fields.Priority priority) {
        if (priority == null) return TicketPriority.MEDIUM;
        return switch (priority.getName().toLowerCase()) {
            case "highest" -> TicketPriority.HIGHEST;
            case "high"    -> TicketPriority.HIGH;
            case "low"     -> TicketPriority.LOW;
            case "lowest"  -> TicketPriority.LOWEST;
            default        -> TicketPriority.MEDIUM;
        };
    }

    public record SyncResult(int ticketsSynced, int errors, String message) {}

    public record SyncStatusResponse(
            boolean syncEnabled,
            LocalDateTime lastSyncedAt,
            String lastSyncStatus,
            long totalTickets,
            String baseUrl,
            List<String> projectKeys
    ) {}

    public record ProjectSyncStatusResponse(
            String projectName,
            String jiraProjectKey,
            long openTickets,
            LocalDateTime lastSyncedAt,
            String syncState
    ) {}
}
