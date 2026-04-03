package com.vymo.bandviz.service;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.JiraTicketRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import com.vymo.bandviz.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraTicketServiceTest {

    @Mock
    private DeveloperRepository developerRepository;

    @Mock
    private JiraTicketRepository jiraTicketRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private JiraProperties jiraProperties;

    @InjectMocks
    private JiraTicketService jiraTicketService;

    @Test
    void findTickets_shouldResolveTeamTicketsFromProjectsAndDevelopers() {
        Team team = Team.builder().id(4L).name("Collections").active(true).build();
        Project project = Project.builder()
                .id(9L)
                .name("Fullerton")
                .jiraProjectKey("FICC")
                .team(team)
                .active(true)
                .build();
        Developer developer = Developer.builder()
                .id(11L)
                .name("Tejas")
                .jiraUsername("tejas@getvymo.com")
                .team(team)
                .active(true)
                .build();
        JiraTicket projectTicket = JiraTicket.builder()
                .id(100L)
                .ticketKey("FICC-101")
                .projectKey("FICC")
                .lastSyncedAt(LocalDateTime.now())
                .build();
        JiraTicket developerTicket = JiraTicket.builder()
                .id(200L)
                .ticketKey("OPS-44")
                .assigneeJiraUsername("tejas@getvymo.com")
                .projectKey("OPS")
                .lastSyncedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(teamRepository.findById(4L)).thenReturn(Optional.of(team));
        when(projectRepository.findDistinctByActiveTrueAndPermittedTeams_Id(4L)).thenReturn(List.of(project));
        when(developerRepository.findAllByActiveTrueAndTeamId(4L)).thenReturn(List.of(developer));
        when(jiraTicketRepository.findAllByProjectKeyIn(List.of("FICC"))).thenReturn(List.of(projectTicket));
        when(jiraTicketRepository.findAllByAssigneeJiraUsernameIn(List.of("tejas@getvymo.com"))).thenReturn(List.of(developerTicket));
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectRepository.findAllByActiveTrue()).thenReturn(List.of(project));
        when(jiraProperties.getBaseUrl()).thenReturn("https://teamvymo.atlassian.net");

        var response = jiraTicketService.findTickets(null, 4L, null);

        assertEquals(2, response.size());
        assertEquals("Collections", response.get(0).getTeamName());
        assertEquals("https://teamvymo.atlassian.net/browse/FICC-101", response.get(0).getTicketUrl());
    }
}
