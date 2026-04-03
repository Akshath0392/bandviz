package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.domain.enums.DeveloperRole;
import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import com.vymo.bandviz.dto.request.AssignmentRequest;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private DeveloperService developerService;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private AssignmentService assignmentService;

    @Test
    void create_shouldRejectWhenDeveloperAndProjectBelongToDifferentTeams() {
        Team alpha = Team.builder().id(1L).name("Alpha").active(true).build();
        Team beta = Team.builder().id(2L).name("Beta").active(true).build();

        Developer developer = Developer.builder()
                .id(11L)
                .name("Arjun")
                .email("arjun@vymo.com")
                .role(DeveloperRole.DEVELOPER)
                .weeklyCapacityHours(40)
                .team(alpha)
                .active(true)
                .build();

        Project project = Project.builder()
                .id(22L)
                .name("Project X")
                .jiraProjectKey("PROJX")
                .deliveryMode(ProjectDeliveryMode.HYBRID)
                .targetUtilizationPct(70)
                .team(beta)
                .active(true)
                .build();

        AssignmentRequest request = new AssignmentRequest();
        request.setDeveloperId(11L);
        request.setProjectId(22L);
        request.setAllocationPct(40);
        request.setStartDate(LocalDate.of(2026, 4, 3));
        request.setEndDate(LocalDate.of(2026, 4, 30));

        when(developerService.getOrThrow(11L)).thenReturn(developer);
        when(projectService.getOrThrow(22L)).thenReturn(project);

        assertThrows(BusinessException.class, () -> assignmentService.create(request));
    }

    @Test
    void create_shouldAllowWhenDeveloperTeamIsPermittedForProject() {
        Team alpha = Team.builder().id(1L).name("Alpha").active(true).build();
        Team beta = Team.builder().id(2L).name("Beta").active(true).build();

        Developer developer = Developer.builder()
                .id(11L)
                .name("Arjun")
                .email("arjun@vymo.com")
                .role(DeveloperRole.DEVELOPER)
                .weeklyCapacityHours(40)
                .team(alpha)
                .active(true)
                .build();

        Project project = Project.builder()
                .id(22L)
                .name("Shared Project")
                .jiraProjectKey("SHP")
                .deliveryMode(ProjectDeliveryMode.HYBRID)
                .targetUtilizationPct(70)
                .team(beta)
                .permittedTeams(new LinkedHashSet<>(java.util.List.of(alpha, beta)))
                .active(true)
                .build();

        AssignmentRequest request = new AssignmentRequest();
        request.setDeveloperId(11L);
        request.setProjectId(22L);
        request.setAllocationPct(40);
        request.setStartDate(LocalDate.of(2026, 4, 3));
        request.setEndDate(LocalDate.of(2026, 4, 30));

        when(developerService.getOrThrow(11L)).thenReturn(developer);
        when(projectService.getOrThrow(22L)).thenReturn(project);
        when(assignmentRepository.findAllOverlappingForDeveloper(any(), any(), any())).thenReturn(java.util.List.of());
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> assignmentService.create(request));
    }
}
