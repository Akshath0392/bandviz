package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Assignment;
import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.dto.request.AssignmentRequest;
import com.vymo.bandviz.dto.response.AssignmentResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final DeveloperService developerService;
    private final ProjectService projectService;

    public List<AssignmentResponse> findByDeveloper(Long developerId) {
        return assignmentRepository.findAllByDeveloperId(developerId)
                .stream().map(this::toResponse).toList();
    }

    public List<AssignmentResponse> findByProject(Long projectId) {
        return assignmentRepository.findAllByProjectId(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssignmentResponse create(AssignmentRequest request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("endDate must be after startDate");
        }
        Developer developer = developerService.getOrThrow(request.getDeveloperId());
        Project project = projectService.getOrThrow(request.getProjectId());

        Assignment assignment = Assignment.builder()
                .developer(developer)
                .project(project)
                .allocationPct(request.getAllocationPct())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public AssignmentResponse update(Long id, AssignmentRequest request) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
        assignment.setAllocationPct(request.getAllocationPct());
        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void delete(Long id) {
        if (!assignmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Assignment not found: " + id);
        }
        assignmentRepository.deleteById(id);
    }

    private AssignmentResponse toResponse(Assignment a) {
        AssignmentResponse r = new AssignmentResponse();
        r.setId(a.getId());
        r.setDeveloperId(a.getDeveloper().getId());
        r.setDeveloperName(a.getDeveloper().getName());
        r.setProjectId(a.getProject().getId());
        r.setProjectName(a.getProject().getName());
        r.setAllocationPct(a.getAllocationPct());
        r.setStartDate(a.getStartDate());
        r.setEndDate(a.getEndDate());
        return r;
    }
}
