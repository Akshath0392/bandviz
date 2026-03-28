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
                    ? projectRepository.findAllByActiveTrueAndTeamId(teamId)
                    : projectRepository.findAllByTeamId(teamId);
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
                .team(resolveTeam(request.getTeamId()))
                .active(true)
                .build();
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
        project.setTeam(resolveTeam(request.getTeamId()));
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
        r.setActive(p.getActive());
        return r;
    }

    private Team resolveTeam(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }
}
