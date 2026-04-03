package com.vymo.bandviz.domain;

import com.vymo.bandviz.domain.enums.JiraFilterScope;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "jira_sync_filters",
        uniqueConstraints = @UniqueConstraint(name = "uq_jira_sync_filters_scope_project", columnNames = {"scope", "project_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraSyncFilter {

    public static final String GLOBAL_PROJECT_KEY = "__GLOBAL__";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JiraFilterScope scope;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(columnDefinition = "TEXT")
    private String assignees;

    @Column(columnDefinition = "TEXT")
    private String labels;

    @Column(name = "sprint_mode", length = 64)
    private String sprintMode;

    @Column(name = "status_category", length = 64)
    private String statusCategory;

    @Column(columnDefinition = "TEXT")
    private String components;

    @Column(name = "issue_types", columnDefinition = "TEXT")
    private String issueTypes;

    @Column(name = "priority_mode", length = 64)
    private String priorityMode;

    @Column(name = "created_after")
    private LocalDate createdAfter;

    @Column(name = "custom_jql", columnDefinition = "TEXT")
    private String customJql;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
}
