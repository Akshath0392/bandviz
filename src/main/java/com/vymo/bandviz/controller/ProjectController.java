package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.request.ProjectRequest;
import com.vymo.bandviz.dto.response.ProjectResponse;
import com.vymo.bandviz.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List all projects")
    public List<ProjectResponse> findAll(@RequestParam(defaultValue = "true") boolean activeOnly,
                                         @RequestParam(required = false) Long teamId) {
        return projectService.findAll(activeOnly, teamId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ProjectResponse findById(@PathVariable Long id) {
        return projectService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create project")
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate project")
    public void deactivate(@PathVariable Long id) {
        projectService.deactivate(id);
    }
}
