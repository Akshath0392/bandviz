package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

    Optional<JiraTicket> findByTicketKey(String ticketKey);

    List<JiraTicket> findAllByAssigneeJiraUsername(String jiraUsername);

    List<JiraTicket> findAllByProjectKey(String projectKey);

    long countByProjectKeyAndStatusNot(String projectKey, TicketStatus status);

    List<JiraTicket> findAllBySprintName(String sprintName);

    List<JiraTicket> findAllByAssigneeJiraUsernameAndStatusNot(
            String jiraUsername, TicketStatus status);

    long countByAssigneeJiraUsernameAndStatusNot(
            String jiraUsername, TicketStatus status);

    long countByStatus(TicketStatus status);
}
