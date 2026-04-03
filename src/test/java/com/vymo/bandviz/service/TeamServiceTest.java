package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.dto.request.TeamRequest;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import com.vymo.bandviz.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DeveloperRepository developerRepository;

    @InjectMocks
    private TeamService teamService;

    @Test
    void create_shouldPersistTeam() {
        TeamRequest request = new TeamRequest();
        request.setName("Collections BAU");
        request.setDescription("Collection related clients and projects");

        when(teamRepository.existsByName("Collections BAU")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setId(7L);
            return team;
        });

        var response = teamService.create(request);

        assertEquals(7L, response.getId());
        assertEquals("Collections BAU", response.getName());
        assertEquals("Collection related clients and projects", response.getDescription());
    }

    @Test
    void create_shouldThrowWhenTeamNameAlreadyExists() {
        TeamRequest request = new TeamRequest();
        request.setName("Collections BAU");

        when(teamRepository.existsByName("Collections BAU")).thenReturn(true);

        assertThrows(BusinessException.class, () -> teamService.create(request));
    }
}
