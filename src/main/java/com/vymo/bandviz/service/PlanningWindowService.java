package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Sprint;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanningWindowService {

    private final SprintRepository sprintRepository;

    @Value("${bandviz.planning.default-window-days:14}")
    private int defaultWindowDays;

    public PlanningWindow resolveCurrentWindow() {
        return sprintRepository.findByActiveTrue()
                .map(this::fromSprint)
                .orElseGet(this::fallbackWindow);
    }

    public PlanningWindow resolveSprintWindow(Long sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        return fromSprint(sprint);
    }

    private PlanningWindow fromSprint(Sprint sprint) {
        return new PlanningWindow(
                sprint.getName(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                "SPRINT",
                false
        );
    }

    private PlanningWindow fallbackWindow() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(Math.max(defaultWindowDays - 1L, 0L));
        return new PlanningWindow(
                "Current Planning Window",
                start,
                end,
                "KANBAN",
                true
        );
    }
}
