package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.dto.request.ProjectRequest;
import com.vymo.bandviz.dto.response.ProjectResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.ProjectRepository;
import com.vymo.bandviz.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;

    public List<ProjectResponse> findAll(boolean activeOnly, Long teamId) {
        List<Project> projects;
        if (teamId != null) {
            projects = activeOnly
                    ? projectRepository.findDistinctByActiveTrueAndPermittedTeams_Id(teamId)
                    : projectRepository.findDistinctByPermittedTeams_Id(teamId);
        } else {
            projects = activeOnly
                    ? projectRepository.findAllByActiveTrue()
                    : projectRepository.findAll();
        }
        return projects.stream().map(this::toResponse).toList();
    }

    public ProjectResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        if (projectRepository.existsByName(request.getName())) {
            throw new BusinessException("A project named '" + request.getName() + "' already exists");
        }
        Project project = Project.builder()
                .name(request.getName())
                .jiraProjectKey(request.getJiraProjectKey())
                .color(request.getColor())
                .targetUtilizationPct(request.getTargetUtilizationPct())
                .deliveryMode(request.getDeliveryMode())
                .team(resolvePrimaryTeam(request.getTeamId()))
                .active(true)
                .build();
        project.setPermittedTeams(resolvePermittedTeams(request.getPermittedTeamIds(), project.getTeam()));
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = getOrThrow(id);
        project.setName(request.getName());
        project.setJiraProjectKey(request.getJiraProjectKey());
        project.setColor(request.getColor());
        project.setTargetUtilizationPct(request.getTargetUtilizationPct());
        project.setDeliveryMode(request.getDeliveryMode());
        Team primaryTeam = resolvePrimaryTeam(request.getTeamId());
        project.setTeam(primaryTeam);
        project.setPermittedTeams(resolvePermittedTeams(request.getPermittedTeamIds(), primaryTeam));
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void deactivate(Long id) {
        Project project = getOrThrow(id);
        project.setActive(false);
        projectRepository.save(project);
    }

    Project getOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private ProjectResponse toResponse(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setJiraProjectKey(p.getJiraProjectKey());
        r.setColor(p.getColor());
        r.setTargetUtilizationPct(p.getTargetUtilizationPct());
        r.setDeliveryMode(p.getDeliveryMode());
        r.setTeamId(p.getTeam() != null ? p.getTeam().getId() : null);
        r.setTeamName(p.getTeam() != null ? p.getTeam().getName() : null);
        List<Team> permitted = p.getPermittedTeams() == null
                ? List.of()
                : p.getPermittedTeams().stream()
                    .sorted(Comparator.comparing(Team::getId))
                    .toList();
        r.setPermittedTeamIds(permitted.stream().map(Team::getId).toList());
        r.setPermittedTeamNames(permitted.stream().map(Team::getName).toList());
        r.setActive(p.getActive());
        return r;
    }

    private Team resolvePrimaryTeam(Long teamId) {
        if (teamId == null) {
            throw new BusinessException("teamId is required for projects");
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    private Set<Team> resolvePermittedTeams(List<Long> permittedTeamIds, Team primaryTeam) {
        if (primaryTeam == null) {
            throw new BusinessException("teamId is required for projects");
        }

        if (permittedTeamIds == null || permittedTeamIds.isEmpty()) {
            return new LinkedHashSet<>(Set.of(primaryTeam));
        }

        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        permittedTeamIds.stream().filter(id -> id != null).forEach(requestedIds::add);
        requestedIds.add(primaryTeam.getId());

        List<Team> resolvedTeams = teamRepository.findAllById(requestedIds);
        if (resolvedTeams.size() != requestedIds.size()) {
            throw new ResourceNotFoundException("One or more permitted teams were not found");
        }
        return new LinkedHashSet<>(resolvedTeams);
    }
}
