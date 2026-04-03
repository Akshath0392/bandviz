package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.request.TeamRequest;
import com.vymo.bandviz.dto.response.JiraLinkedTeamResponse;
import com.vymo.bandviz.dto.response.TeamResponse;
import com.vymo.bandviz.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Team management and project/developer mapping")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @Operation(summary = "List all teams")
    public List<TeamResponse> findAll(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return teamService.findAll(activeOnly);
    }

    @GetMapping("/jira-linked")
    @Operation(summary = "List active teams that have Jira-linked projects")
    public List<JiraLinkedTeamResponse> jiraLinked() {
        return teamService.findJiraLinkedTeams();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get team by ID")
    public TeamResponse findById(@PathVariable Long id) {
        return teamService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create team")
    public TeamResponse create(@Valid @RequestBody TeamRequest request) {
        return teamService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update team")
    public TeamResponse update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return teamService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate team")
    public void deactivate(@PathVariable Long id) {
        teamService.deactivate(id);
    }
}
