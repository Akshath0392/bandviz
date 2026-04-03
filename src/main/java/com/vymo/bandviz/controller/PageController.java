package com.vymo.bandviz.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home() {
        return "forward:/home.html";
    }

    @GetMapping("/team-dashboard")
    public String teamDashboard() {
        return "forward:/team-dashboard.html";
    }

    @GetMapping("/developer-detail")
    public String developerDetail() {
        return "forward:/developer-detail.html";
    }

    @GetMapping("/leave-calendar")
    public String leaveCalendar() {
        return "forward:/leave-calendar.html";
    }

    @GetMapping("/capacity-planner")
    public String capacityPlanner() {
        return "forward:/capacity-planner.html";
    }

    @GetMapping("/jira-sync")
    public String jiraSync() {
        return "forward:/jira-sync.html";
    }

    @GetMapping("/settings")
    public String settings() {
        return "forward:/settings.html";
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
