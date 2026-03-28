package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.request.DeveloperRequest;
import com.vymo.bandviz.dto.response.DeveloperResponse;
import com.vymo.bandviz.service.DeveloperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/developers")
@RequiredArgsConstructor
@Tag(name = "Developers", description = "Developer management")
public class DeveloperController {

    private final DeveloperService developerService;

    @GetMapping
    @Operation(summary = "List all developers")
    public List<DeveloperResponse> findAll(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) Long teamId) {
        return developerService.findAll(activeOnly, teamId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get developer by ID")
    public DeveloperResponse findById(@PathVariable Long id) {
        return developerService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a developer")
    public DeveloperResponse create(@Valid @RequestBody DeveloperRequest request) {
        return developerService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a developer")
    public DeveloperResponse update(@PathVariable Long id,
                                    @Valid @RequestBody DeveloperRequest request) {
        return developerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a developer")
    public void deactivate(@PathVariable Long id) {
        developerService.deactivate(id);
    }
}
