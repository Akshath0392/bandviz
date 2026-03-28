package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

    Optional<JiraTicket> findByTicketKey(String ticketKey);

    List<JiraTicket> findAllByAssigneeJiraUsername(String jiraUsername);

    List<JiraTicket> findAllByAssigneeJiraUsernameIn(List<String> jiraUsernames);

    List<JiraTicket> findAllByProjectKey(String projectKey);

    List<JiraTicket> findAllByProjectKeyIn(List<String> projectKeys);

    long countByProjectKeyAndStatusNot(String projectKey, TicketStatus status);

    List<JiraTicket> findAllBySprintName(String sprintName);

    List<JiraTicket> findAllByAssigneeJiraUsernameAndStatusNot(
            String jiraUsername, TicketStatus status);

    List<JiraTicket> findAllByAssigneeJiraUsernameInAndStatusNot(
            List<String> jiraUsernames, TicketStatus status);

    long countByAssigneeJiraUsernameAndStatusNot(
            String jiraUsername, TicketStatus status);

    long countByStatus(TicketStatus status);

    long countByStatusAndLastSyncedAtGreaterThanEqual(TicketStatus status, LocalDateTime timestamp);
}
