package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.request.AssignmentRequest;
import com.vymo.bandviz.dto.response.AssignmentResponse;
import com.vymo.bandviz.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/project-allocations", "/api/assignments"})
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Developer project allocation")
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping
    @Operation(summary = "List assignments by developer or project")
    public List<AssignmentResponse> findAll(
            @RequestParam(required = false) Long developerId,
            @RequestParam(required = false) Long projectId) {

        if (developerId != null) {
            return assignmentService.findByDeveloper(developerId);
        }
        if (projectId != null) {
            return assignmentService.findByProject(projectId);
        }
        throw new IllegalArgumentException("Either developerId or projectId query param is required");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create assignment")
    public AssignmentResponse create(@Valid @RequestBody AssignmentRequest request) {
        return assignmentService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update assignment")
    public AssignmentResponse update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete assignment")
    public void delete(@PathVariable Long id) {
        assignmentService.delete(id);
    }
}
