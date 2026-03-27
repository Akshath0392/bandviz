package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.request.SprintRequest;
import com.vymo.bandviz.dto.response.SprintResponse;
import com.vymo.bandviz.service.SprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
@Tag(name = "Sprints", description = "Sprint management")
public class SprintController {

    private final SprintService sprintService;

    @GetMapping
    @Operation(summary = "List sprints")
    public List<SprintResponse> findAll() {
        return sprintService.findAll();
    }

    @GetMapping({"/current", "/active"})
    @Operation(summary = "Get active sprint")
    public SprintResponse findActive() {
        return sprintService.findActive();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create sprint")
    public SprintResponse create(@Valid @RequestBody SprintRequest request) {
        return sprintService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update sprint")
    public SprintResponse update(@PathVariable Long id, @Valid @RequestBody SprintRequest request) {
        return sprintService.update(id, request);
    }
}
