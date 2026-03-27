package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.Leave;
import com.vymo.bandviz.domain.enums.LeaveStatus;
import com.vymo.bandviz.repository.LeaveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private DeveloperService developerService;

    @InjectMocks
    private LeaveService leaveService;

    @Test
    void countWorkingDays_shouldSkipWeekends() {
        LocalDate start = LocalDate.of(2026, 3, 23); // Monday
        LocalDate end = LocalDate.of(2026, 3, 29);   // Sunday

        long workingDays = leaveService.countWorkingDays(start, end);

        assertEquals(5, workingDays);
    }

    @Test
    void countApprovedLeaveDays_shouldCapAtWorkingDaysInRange() {
        Long developerId = 1L;
        LocalDate start = LocalDate.of(2026, 3, 23); // Monday
        LocalDate end = LocalDate.of(2026, 3, 27);   // Friday => 5 working days

        Developer developer = Developer.builder().id(developerId).name("Arjun").build();
        Leave leave1 = Leave.builder()
                .developer(developer)
                .startDate(LocalDate.of(2026, 3, 23))
                .endDate(LocalDate.of(2026, 3, 27))
                .status(LeaveStatus.APPROVED)
                .build();
        Leave leave2 = Leave.builder()
                .developer(developer)
                .startDate(LocalDate.of(2026, 3, 24))
                .endDate(LocalDate.of(2026, 3, 26))
                .status(LeaveStatus.APPROVED)
                .build();

        when(leaveRepository.findApprovedLeavesInRange(developerId, start, end))
                .thenReturn(List.of(leave1, leave2));

        long leaveDays = leaveService.countApprovedLeaveDays(developerId, start, end);

        assertEquals(5, leaveDays);
    }
}
