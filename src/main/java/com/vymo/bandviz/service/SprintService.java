package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Sprint;
import com.vymo.bandviz.dto.request.SprintRequest;
import com.vymo.bandviz.dto.response.SprintResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SprintService {

    private final SprintRepository sprintRepository;

    public List<SprintResponse> findAll() {
        return sprintRepository.findAll().stream().map(this::toResponse).toList();
    }

    public SprintResponse findActive() {
        Sprint sprint = sprintRepository.findByActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active sprint found"));
        return toResponse(sprint);
    }

    @Transactional
    public SprintResponse create(SprintRequest request) {
        validateDates(request.getStartDate(), request.getEndDate());
        if (Boolean.TRUE.equals(request.getActive())) {
            deactivateCurrentActiveSprint();
        }

        Sprint sprint = Sprint.builder()
                .name(request.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();
        return toResponse(sprintRepository.save(sprint));
    }

    @Transactional
    public SprintResponse update(Long id, SprintRequest request) {
        validateDates(request.getStartDate(), request.getEndDate());
        Sprint sprint = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));

        if (Boolean.TRUE.equals(request.getActive()) && !Boolean.TRUE.equals(sprint.getActive())) {
            deactivateCurrentActiveSprint();
        }

        sprint.setName(request.getName());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setActive(Boolean.TRUE.equals(request.getActive()));
        return toResponse(sprintRepository.save(sprint));
    }

    private void deactivateCurrentActiveSprint() {
        sprintRepository.findByActiveTrue().ifPresent(active -> {
            active.setActive(false);
            sprintRepository.save(active);
        });
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessException("endDate must be on or after startDate");
        }
    }

    private SprintResponse toResponse(Sprint s) {
        SprintResponse response = new SprintResponse();
        response.setId(s.getId());
        response.setName(s.getName());
        response.setStartDate(s.getStartDate());
        response.setEndDate(s.getEndDate());
        response.setActive(s.getActive());
        response.setTotalWorkingDays(countWorkingDays(s.getStartDate(), s.getEndDate()));
        return response;
    }

    private long countWorkingDays(LocalDate start, LocalDate end) {
        long days = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DayOfWeek d = cursor.getDayOfWeek();
            if (d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY) {
                days++;
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }
}
