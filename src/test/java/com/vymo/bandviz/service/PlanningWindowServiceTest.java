package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Sprint;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.SprintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningWindowServiceTest {

    @Mock
    private SprintRepository sprintRepository;

    @InjectMocks
    private PlanningWindowService planningWindowService;

    @Test
    void resolveCurrentWindow_shouldReturnActiveSprintWhenPresent() {
        Sprint sprint = Sprint.builder()
                .id(1L)
                .name("Sprint 42")
                .startDate(LocalDate.of(2026, 3, 17))
                .endDate(LocalDate.of(2026, 3, 28))
                .active(true)
                .build();

        when(sprintRepository.findByActiveTrue()).thenReturn(Optional.of(sprint));

        PlanningWindow result = planningWindowService.resolveCurrentWindow();

        assertEquals("Sprint 42", result.name());
        assertEquals("SPRINT", result.mode());
        assertFalse(result.fallback());
    }

    @Test
    void resolveCurrentWindow_shouldFallBackToRollingWindowWhenNoActiveSprint() {
        ReflectionTestUtils.setField(planningWindowService, "defaultWindowDays", 14);
        when(sprintRepository.findByActiveTrue()).thenReturn(Optional.empty());

        PlanningWindow result = planningWindowService.resolveCurrentWindow();

        assertEquals("Current Planning Window", result.name());
        assertEquals(LocalDate.now(), result.startDate());
        assertEquals(LocalDate.now().plusDays(13), result.endDate());
        assertEquals("KANBAN", result.mode());
        assertTrue(result.fallback());
    }

    @Test
    void resolveSprintWindow_shouldThrowWhenSprintMissing() {
        when(sprintRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> planningWindowService.resolveSprintWindow(99L));
    }
}
