package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import com.vymo.bandviz.dto.request.ProjectRequest;
import com.vymo.bandviz.dto.response.ProjectResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.repository.ProjectRepository;
import com.vymo.bandviz.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void create_shouldPersistDeliveryMode() {
        ProjectRequest request = new ProjectRequest();
        request.setName("Collections Productisation POD");
        request.setJiraProjectKey("COLLXPD");
        request.setColor("#1d4ed8");
        request.setTargetUtilizationPct(75);
        request.setDeliveryMode(ProjectDeliveryMode.KANBAN);
        request.setTeamId(8L);

        Team team = Team.builder().id(8L).name("Collections").active(true).build();

        when(projectRepository.existsByName("Collections Productisation POD")).thenReturn(false);
        when(teamRepository.findById(8L)).thenReturn(Optional.of(team));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(22L);
            return project;
        });

        ProjectResponse response = projectService.create(request);

        assertEquals(22L, response.getId());
        assertEquals(ProjectDeliveryMode.KANBAN, response.getDeliveryMode());
        assertEquals("COLLXPD", response.getJiraProjectKey());
        assertEquals(75, response.getTargetUtilizationPct());
        assertEquals(8L, response.getTeamId());
        assertEquals("Collections", response.getTeamName());
    }

    @Test
    void update_shouldOverwriteDeliveryMode() {
        Project existing = Project.builder()
                .id(28L)
                .name("Fullerton")
                .jiraProjectKey("FICC")
                .targetUtilizationPct(70)
                .deliveryMode(ProjectDeliveryMode.HYBRID)
                .active(true)
                .build();

        ProjectRequest request = new ProjectRequest();
        request.setName("Fullerton");
        request.setJiraProjectKey("FICC");
        request.setColor("#2563eb");
        request.setTargetUtilizationPct(65);
        request.setDeliveryMode(ProjectDeliveryMode.SPRINT);

        when(projectRepository.findById(28L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.update(28L, request);

        assertEquals(ProjectDeliveryMode.SPRINT, response.getDeliveryMode());
        assertEquals(65, response.getTargetUtilizationPct());
        assertEquals("#2563eb", response.getColor());
    }

    @Test
    void create_shouldThrowWhenNameAlreadyExists() {
        ProjectRequest request = new ProjectRequest();
        request.setName("Fullerton");

        when(projectRepository.existsByName("Fullerton")).thenReturn(true);

        assertThrows(BusinessException.class, () -> projectService.create(request));
    }
}
