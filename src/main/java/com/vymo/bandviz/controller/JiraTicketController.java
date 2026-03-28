package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.response.JiraTicketResponse;
import com.vymo.bandviz.service.JiraTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jira-tickets")
@RequiredArgsConstructor
@Tag(name = "Jira Tickets", description = "Read Jira ticket data synced into BandViz")
public class JiraTicketController {

    private final JiraTicketService jiraTicketService;

    @GetMapping
    @Operation(summary = "List Jira tickets using developer, team, or project mapping")
    public List<JiraTicketResponse> findTickets(@RequestParam(required = false) Long developerId,
                                                @RequestParam(required = false) Long teamId,
                                                @RequestParam(required = false) Long projectId) {
        return jiraTicketService.findTickets(developerId, teamId, projectId);
    }
}
