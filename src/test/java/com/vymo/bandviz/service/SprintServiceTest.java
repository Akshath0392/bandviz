package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Sprint;
import com.vymo.bandviz.dto.request.SprintRequest;
import com.vymo.bandviz.dto.response.SprintResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.repository.SprintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock
    private SprintRepository sprintRepository;

    @InjectMocks
    private SprintService sprintService;

    @Test
    void create_activeSprintShouldDeactivateExistingActiveSprint() {
        Sprint existingActive = Sprint.builder()
                .id(1L)
                .name("Sprint 41")
                .startDate(LocalDate.of(2026, 3, 3))
                .endDate(LocalDate.of(2026, 3, 14))
                .active(true)
                .build();

        SprintRequest request = new SprintRequest();
        request.setName("Sprint 42");
        request.setStartDate(LocalDate.of(2026, 3, 17));
        request.setEndDate(LocalDate.of(2026, 3, 28));
        request.setActive(true);

        when(sprintRepository.findByActiveTrue()).thenReturn(Optional.of(existingActive));
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SprintResponse response = sprintService.create(request);

        assertFalse(existingActive.getActive());
        assertTrue(response.getActive());
    }

    @Test
    void create_shouldThrowWhenEndDateBeforeStartDate() {
        SprintRequest request = new SprintRequest();
        request.setName("Invalid Sprint");
        request.setStartDate(LocalDate.of(2026, 3, 20));
        request.setEndDate(LocalDate.of(2026, 3, 10));

        assertThrows(BusinessException.class, () -> sprintService.create(request));
    }
}
