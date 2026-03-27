package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.enums.DeveloperRole;
import com.vymo.bandviz.dto.request.DeveloperRequest;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.repository.DeveloperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceTest {

    @Mock
    private DeveloperRepository developerRepository;

    @InjectMocks
    private DeveloperService developerService;

    @Test
    void create_shouldThrowWhenEmailAlreadyExists() {
        DeveloperRequest request = new DeveloperRequest();
        request.setName("Arjun");
        request.setEmail("arjun@vymo.com");
        request.setRole(DeveloperRole.DEVELOPER);
        request.setWeeklyCapacityHours(40);

        when(developerRepository.existsByEmail("arjun@vymo.com")).thenReturn(true);

        assertThrows(BusinessException.class, () -> developerService.create(request));
    }

    @Test
    void deactivate_shouldSetActiveFalse() {
        Developer developer = Developer.builder()
                .id(10L)
                .name("Neha")
                .email("neha@vymo.com")
                .role(DeveloperRole.TECH_LEAD)
                .weeklyCapacityHours(40)
                .active(true)
                .build();

        when(developerRepository.findById(10L)).thenReturn(Optional.of(developer));
        when(developerRepository.save(any(Developer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        developerService.deactivate(10L);

        assertFalse(developer.getActive());
        verify(developerRepository).save(developer);
    }
}
