package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.Leave;
import com.vymo.bandviz.domain.enums.LeaveStatus;
import com.vymo.bandviz.dto.request.LeaveRequest;
import com.vymo.bandviz.dto.response.LeaveResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final DeveloperService developerService;

    public List<LeaveResponse> findByDeveloper(Long developerId) {
        return leaveRepository.findAllByDeveloperId(developerId)
                .stream().map(this::toResponse).toList();
    }

    public List<LeaveResponse> findPending() {
        return leaveRepository.findAllByStatus(LeaveStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    public List<LeaveResponse> findInRange(LocalDate startDate, LocalDate endDate) {
        return leaveRepository.findAllApprovedLeavesInRange(startDate, endDate)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public LeaveResponse apply(LeaveRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("endDate must be on or after startDate");
        }
        Developer developer = developerService.getOrThrow(request.getDeveloperId());
        Leave leave = Leave.builder()
                .developer(developer)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(LeaveStatus.PENDING)
                .notes(request.getNotes())
                .build();
        return toResponse(leaveRepository.save(leave));
    }

    @Transactional
    public LeaveResponse approve(Long id) {
        return updateStatus(id, LeaveStatus.APPROVED);
    }

    @Transactional
    public LeaveResponse reject(Long id) {
        return updateStatus(id, LeaveStatus.REJECTED);
    }

    @Transactional
    public void delete(Long id) {
        Leave leave = getOrThrow(id);
        if (leave.getStatus() == LeaveStatus.APPROVED) {
            throw new BusinessException("Cannot delete an approved leave. Reject it first.");
        }
        leaveRepository.deleteById(id);
    }

    // Count approved working days on leave for a developer in a date range
    public long countApprovedLeaveDays(Long developerId, LocalDate startDate, LocalDate endDate) {
        List<Leave> leaves = leaveRepository.findApprovedLeavesInRange(developerId, startDate, endDate);
        Set<LocalDate> leaveDays = new HashSet<>();
        leaves.forEach(leave -> {
            LocalDate overlapStart = leave.getStartDate().isBefore(startDate) ? startDate : leave.getStartDate();
            LocalDate overlapEnd = leave.getEndDate().isAfter(endDate) ? endDate : leave.getEndDate();
            LocalDate cursor = overlapStart;
            while (!cursor.isAfter(overlapEnd)) {
                DayOfWeek dayOfWeek = cursor.getDayOfWeek();
                if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                    leaveDays.add(cursor);
                }
                cursor = cursor.plusDays(1);
            }
        });
        return leaveDays.size();
    }

    // Count working days (Mon–Fri) between two dates, inclusive
    public long countWorkingDays(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return 0;
        }
        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long fullWeeks = totalDays / 7;
        long workingDays = fullWeeks * 5;
        long remainingDays = totalDays % 7;

        for (long i = 0; i < remainingDays; i++) {
            DayOfWeek dayOfWeek = start.plusDays(fullWeeks * 7 + i).getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                workingDays++;
            }
        }
        return workingDays;
    }

    private LeaveResponse toResponse(Leave l) {
        LeaveResponse r = new LeaveResponse();
        r.setId(l.getId());
        r.setDeveloperId(l.getDeveloper().getId());
        r.setDeveloperName(l.getDeveloper().getName());
        r.setLeaveType(l.getLeaveType());
        r.setStartDate(l.getStartDate());
        r.setEndDate(l.getEndDate());
        r.setStatus(l.getStatus());
        r.setNotes(l.getNotes());
        r.setDurationDays(countWorkingDays(l.getStartDate(), l.getEndDate()));
        return r;
    }

    private LeaveResponse updateStatus(Long id, LeaveStatus newStatus) {
        Leave leave = getOrThrow(id);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("Leave is already " + leave.getStatus());
        }
        leave.setStatus(newStatus);
        return toResponse(leaveRepository.save(leave));
    }

    private Leave getOrThrow(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + id));
    }
}
