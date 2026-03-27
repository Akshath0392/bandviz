package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.dto.request.ProjectRequest;
import com.vymo.bandviz.dto.response.ProjectResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public List<ProjectResponse> findAll(boolean activeOnly) {
        List<Project> projects = activeOnly
                ? projectRepository.findAllByActiveTrue()
                : projectRepository.findAll();
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
        r.setActive(p.getActive());
        return r;
    }
}
