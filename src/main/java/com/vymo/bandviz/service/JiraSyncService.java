package com.vymo.bandviz.service;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.domain.JiraSyncFilter;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.enums.JiraFilterScope;
import com.vymo.bandviz.domain.enums.TicketPriority;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.integration.JiraClient;
import com.vymo.bandviz.integration.dto.JiraIssueResponse;
import com.vymo.bandviz.integration.dto.JiraSearchResponse;
import com.vymo.bandviz.repository.JiraSyncFilterRepository;
import com.vymo.bandviz.repository.JiraTicketRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraSyncService {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\border\\s+by\\b", Pattern.CASE_INSENSITIVE);

    private final JiraClient jiraClient;
    private final JiraTicketRepository jiraTicketRepository;
    private final ProjectRepository projectRepository;
    private final JiraSyncFilterRepository jiraSyncFilterRepository;
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
                .map(this::normalizeProjectKey)
                .distinct()
                .toList();

        if (!configuredKeys.isEmpty()) {
            log.info("Using Jira project keys from configuration: {}", configuredKeys);
            return configuredKeys;
        }

        List<String> projectKeys = projectRepository.findAllByActiveTrue().stream()
                .filter(p -> p.getJiraProjectKey() != null && !p.getJiraProjectKey().isBlank())
                .map(p -> normalizeProjectKey(p.getJiraProjectKey()))
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

        SyncFilterPayload effectiveFilter = getEffectiveFilter(projectKey);
        String jql = buildProjectJql(projectKey, effectiveFilter);

        while (true) {
            JiraSearchResponse response = jiraClient.fetchOpenIssues(projectKey, jql, nextPageToken);
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
                    String projectKey = normalizeProjectKey(project.getJiraProjectKey());
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

    public SyncFiltersResponse getFilters() {
        SyncFilterPayload global = getGlobalFilter();
        List<ProjectFilterOverrideResponse> projectOverrides = projectRepository.findAllByActiveTrue().stream()
                .filter(project -> project.getJiraProjectKey() != null && !project.getJiraProjectKey().isBlank())
                .map(project -> {
                    String projectKey = normalizeProjectKey(project.getJiraProjectKey());
                    Optional<JiraSyncFilter> override = jiraSyncFilterRepository
                            .findByScopeAndProjectKey(JiraFilterScope.PROJECT, projectKey);
                    SyncFilterPayload projectPayload = override.map(this::toPayload).orElse(defaultFilter());
                    SyncFilterPayload effective = mergeFilters(global, projectPayload);
                    return new ProjectFilterOverrideResponse(
                            project.getName(),
                            projectKey,
                            projectPayload,
                            buildProjectJql(projectKey, effective),
                            isEmptyFilter(projectPayload)
                    );
                })
                .toList();

        return new SyncFiltersResponse(global, projectOverrides);
    }

    @Transactional
    public SyncFilterPayload saveGlobalFilter(SyncFilterPayload payload) {
        JiraSyncFilter entity = jiraSyncFilterRepository
                .findByScopeAndProjectKey(JiraFilterScope.GLOBAL, JiraSyncFilter.GLOBAL_PROJECT_KEY)
                .orElse(JiraSyncFilter.builder()
                        .scope(JiraFilterScope.GLOBAL)
                        .projectKey(JiraSyncFilter.GLOBAL_PROJECT_KEY)
                        .build());

        applyPayload(entity, payload);
        jiraSyncFilterRepository.save(entity);
        return toPayload(entity);
    }

    @Transactional
    public SyncFilterPayload saveProjectFilter(String projectKey, SyncFilterPayload payload) {
        String normalizedProjectKey = normalizeProjectKey(projectKey);
        JiraSyncFilter entity = jiraSyncFilterRepository
                .findByScopeAndProjectKey(JiraFilterScope.PROJECT, normalizedProjectKey)
                .orElse(JiraSyncFilter.builder()
                        .scope(JiraFilterScope.PROJECT)
                        .projectKey(normalizedProjectKey)
                        .build());

        applyPayload(entity, payload);
        jiraSyncFilterRepository.save(entity);
        return toPayload(entity);
    }

    @Transactional
    public void resetGlobalFilter() {
        jiraSyncFilterRepository.deleteByScopeAndProjectKey(JiraFilterScope.GLOBAL, JiraSyncFilter.GLOBAL_PROJECT_KEY);
    }

    @Transactional
    public void resetProjectFilter(String projectKey) {
        jiraSyncFilterRepository.deleteByScopeAndProjectKey(JiraFilterScope.PROJECT, normalizeProjectKey(projectKey));
    }

    public JqlPreviewResponse previewJql(String projectKey) {
        String normalizedProjectKey = normalizeProjectKey(projectKey);
        String jql = buildProjectJql(normalizedProjectKey, getEffectiveFilter(normalizedProjectKey));
        return new JqlPreviewResponse(normalizedProjectKey, jql);
    }

    private SyncFilterPayload getGlobalFilter() {
        return jiraSyncFilterRepository
                .findByScopeAndProjectKey(JiraFilterScope.GLOBAL, JiraSyncFilter.GLOBAL_PROJECT_KEY)
                .map(this::toPayload)
                .orElse(defaultFilter());
    }

    private SyncFilterPayload getEffectiveFilter(String projectKey) {
        String normalizedProjectKey = normalizeProjectKey(projectKey);
        SyncFilterPayload global = getGlobalFilter();
        SyncFilterPayload project = jiraSyncFilterRepository
                .findByScopeAndProjectKey(JiraFilterScope.PROJECT, normalizedProjectKey)
                .map(this::toPayload)
                .orElse(defaultFilter());

        return mergeFilters(global, project);
    }

    private SyncFilterPayload mergeFilters(SyncFilterPayload global, SyncFilterPayload project) {
        return new SyncFilterPayload(
                mergeList(global.assignees(), project.assignees()),
                mergeList(global.labels(), project.labels()),
                firstNonBlank(project.sprintMode(), global.sprintMode(), "all"),
                firstNonBlank(project.statusCategory(), global.statusCategory(), "not-done"),
                mergeList(global.components(), project.components()),
                mergeList(global.issueTypes(), project.issueTypes()),
                firstNonBlank(project.priorityMode(), global.priorityMode(), "all"),
                project.createdAfter() != null ? project.createdAfter() : global.createdAfter(),
                firstNonBlank(project.customJql(), global.customJql(), null)
        );
    }

    private void applyPayload(JiraSyncFilter entity, SyncFilterPayload payload) {
        SyncFilterPayload safe = payload == null ? defaultFilter() : payload;
        entity.setAssignees(joinCsv(normalizeList(safe.assignees())));
        entity.setLabels(joinCsv(normalizeList(safe.labels())));
        entity.setSprintMode(normalizeSingle(safe.sprintMode()));
        entity.setStatusCategory(normalizeSingle(safe.statusCategory()));
        entity.setComponents(joinCsv(normalizeList(safe.components())));
        entity.setIssueTypes(joinCsv(normalizeList(safe.issueTypes())));
        entity.setPriorityMode(normalizeSingle(safe.priorityMode()));
        entity.setCreatedAfter(safe.createdAfter());
        entity.setCustomJql(normalizeSingle(safe.customJql()));
    }

    private SyncFilterPayload toPayload(JiraSyncFilter entity) {
        return new SyncFilterPayload(
                splitCsv(entity.getAssignees()),
                splitCsv(entity.getLabels()),
                firstNonBlank(entity.getSprintMode(), "all"),
                firstNonBlank(entity.getStatusCategory(), "not-done"),
                splitCsv(entity.getComponents()),
                splitCsv(entity.getIssueTypes()),
                firstNonBlank(entity.getPriorityMode(), "all"),
                entity.getCreatedAfter(),
                entity.getCustomJql()
        );
    }

    private SyncFilterPayload defaultFilter() {
        return new SyncFilterPayload(List.of(), List.of(), "all", "not-done", List.of(), List.of(), "all", null, null);
    }

    private boolean isEmptyFilter(SyncFilterPayload payload) {
        if (payload == null) {
            return true;
        }
        return normalizeList(payload.assignees()).isEmpty()
                && normalizeList(payload.labels()).isEmpty()
                && normalizeList(payload.components()).isEmpty()
                && normalizeList(payload.issueTypes()).isEmpty()
                && isBlank(payload.createdAfter() == null ? null : payload.createdAfter().toString())
                && isBlank(payload.customJql())
                && isBlank(payload.sprintMode())
                && isBlank(payload.priorityMode())
                && isBlank(payload.statusCategory());
    }

    private String buildProjectJql(String projectKey, SyncFilterPayload payload) {
        String safeProjectKey = sanitizeJqlLiteral(projectKey);
        SyncFilterPayload safe = payload == null ? defaultFilter() : payload;

        if (!isBlank(safe.customJql())) {
            String custom = removeOrderBy(safe.customJql().trim());
            return String.format("project = \"%s\" AND (%s) ORDER BY updated DESC", safeProjectKey, custom);
        }

        List<String> parts = new ArrayList<>();
        parts.add(String.format("project = \"%s\"", safeProjectKey));

        switch (firstNonBlank(safe.statusCategory(), "not-done")) {
            case "all" -> {
            }
            case "to-do" -> parts.add("statusCategory = \"To Do\"");
            case "in-progress" -> parts.add("statusCategory = \"In Progress\"");
            default -> parts.add("statusCategory != Done");
        }

        addInClause(parts, "assignee", safe.assignees());
        addInClause(parts, "labels", safe.labels());
        addInClause(parts, "component", safe.components());
        addInClause(parts, "issuetype", safe.issueTypes());

        String sprintMode = firstNonBlank(safe.sprintMode(), "all");
        if (!"all".equalsIgnoreCase(sprintMode)) {
            if ("active".equalsIgnoreCase(sprintMode)) {
                parts.add("sprint IN openSprints()");
            } else if ("backlog".equalsIgnoreCase(sprintMode)) {
                parts.add("sprint IS EMPTY");
            } else {
                parts.add(String.format("sprint = \"%s\"", sanitizeJqlLiteral(sprintMode)));
            }
        }

        String priorityMode = firstNonBlank(safe.priorityMode(), "all");
        switch (priorityMode) {
            case "highest" -> parts.add("priority = Highest");
            case "high-up" -> parts.add("priority IN (Highest, High)");
            case "medium-up" -> parts.add("priority IN (Highest, High, Medium)");
            default -> {
            }
        }

        if (safe.createdAfter() != null) {
            parts.add(String.format("created >= \"%s\"", safe.createdAfter()));
        }

        return String.join(" AND ", parts) + " ORDER BY updated DESC";
    }

    private void addInClause(List<String> parts, String fieldName, List<String> values) {
        List<String> cleaned = normalizeList(values);
        if (cleaned.isEmpty()) {
            return;
        }
        String joined = cleaned.stream()
                .map(this::sanitizeJqlLiteral)
                .map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        parts.add(String.format("%s IN (%s)", fieldName, joined));
    }

    private String removeOrderBy(String jql) {
        if (isBlank(jql)) {
            return jql;
        }
        return ORDER_BY_PATTERN.split(jql, 2)[0].trim();
    }

    private List<String> splitCsv(String csv) {
        if (isBlank(csv)) {
            return List.of();
        }
        return Stream.of(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return new ArrayList<>(normalized);
    }

    private String normalizeSingle(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private List<String> mergeList(List<String> global, List<String> project) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(normalizeList(global));
        merged.addAll(normalizeList(project));
        return new ArrayList<>(merged);
    }

    private String sanitizeJqlLiteral(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizeProjectKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("projectKey must not be blank");
        }
        String trimmed = value.trim().toUpperCase();
        if (!trimmed.matches("[A-Z0-9_.-]+")) {
            throw new IllegalArgumentException("projectKey contains unsupported characters");
        }
        return trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
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

    public record SyncFilterPayload(
            List<String> assignees,
            List<String> labels,
            String sprintMode,
            String statusCategory,
            List<String> components,
            List<String> issueTypes,
            String priorityMode,
            LocalDate createdAfter,
            String customJql
    ) {}

    public record SyncFiltersResponse(
            SyncFilterPayload globalFilter,
            List<ProjectFilterOverrideResponse> projectOverrides
    ) {}

    public record ProjectFilterOverrideResponse(
            String projectName,
            String jiraProjectKey,
            SyncFilterPayload filter,
            String effectiveJql,
            boolean usesGlobalOnly
    ) {}

    public record JqlPreviewResponse(String projectKey, String jql) {}
}
