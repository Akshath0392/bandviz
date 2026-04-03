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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        validateTeamAlignment(developer, project);
        validateAllocationCap(developer.getId(), request.getAllocationPct(), request.getStartDate(), request.getEndDate(), null);

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
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("endDate must be after startDate");
        }
        validateAllocationCap(assignment.getDeveloper().getId(), request.getAllocationPct(), request.getStartDate(), request.getEndDate(), id);
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

    private void validateAllocationCap(Long developerId, Integer requestedAllocationPct, LocalDate startDate, LocalDate endDate, Long excludedAssignmentId) {
        if (requestedAllocationPct == null || requestedAllocationPct < 0 || requestedAllocationPct > 100) {
            throw new BusinessException("allocationPct must be between 0 and 100");
        }

        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.of(9999, 12, 31);
        List<Assignment> overlappingAssignments = assignmentRepository.findAllOverlappingForDeveloper(developerId, startDate, effectiveEnd).stream()
                .filter(assignment -> excludedAssignmentId == null || !assignment.getId().equals(excludedAssignmentId))
                .toList();

        Set<LocalDate> checkpoints = new HashSet<>();
        checkpoints.add(startDate);
        checkpoints.add(effectiveEnd);
        overlappingAssignments.forEach(assignment -> {
            if (!assignment.getStartDate().isBefore(startDate) && !assignment.getStartDate().isAfter(effectiveEnd)) {
                checkpoints.add(assignment.getStartDate());
            }
            if (assignment.getEndDate() != null) {
                if (!assignment.getEndDate().isBefore(startDate) && !assignment.getEndDate().isAfter(effectiveEnd)) {
                    checkpoints.add(assignment.getEndDate());
                }
                LocalDate nextDay = assignment.getEndDate().plusDays(1);
                if (!nextDay.isBefore(startDate) && !nextDay.isAfter(effectiveEnd)) {
                    checkpoints.add(nextDay);
                }
            }
        });

        boolean exceedsLimit = checkpoints.stream()
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(effectiveEnd))
                .anyMatch(date -> requestedAllocationPct + overlappingAssignments.stream()
                        .filter(assignment -> isActiveOnDate(assignment, date))
                        .mapToInt(Assignment::getAllocationPct)
                        .sum() > 100);

        if (exceedsLimit) {
            throw new BusinessException("Total overlapping allocation cannot exceed 100% for the developer");
        }
    }

    private boolean isActiveOnDate(Assignment assignment, LocalDate date) {
        return !assignment.getStartDate().isAfter(date)
                && (assignment.getEndDate() == null || !assignment.getEndDate().isBefore(date));
    }

    private void validateTeamAlignment(Developer developer, Project project) {
        Long developerTeamId = developer.getTeam() != null ? developer.getTeam().getId() : null;
        boolean teamAllowed = project.getPermittedTeams() != null
                && developerTeamId != null
                && project.getPermittedTeams().stream().anyMatch(team -> developerTeamId.equals(team.getId()));
        if (!teamAllowed) {
            throw new BusinessException("Developer team is not permitted for the selected project");
        }
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
