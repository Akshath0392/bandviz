package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.request.LeaveRequest;
import com.vymo.bandviz.dto.response.LeaveResponse;
import com.vymo.bandviz.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/leave-requests", "/api/leaves"})
@RequiredArgsConstructor
@Tag(name = "Leaves", description = "Leave management")
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping
    @Operation(summary = "List leaves")
    public List<LeaveResponse> findAll(
            @RequestParam(required = false) Long developerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (developerId != null) {
            return leaveService.findByDeveloper(developerId);
        }
        if (startDate != null && endDate != null) {
            return leaveService.findInRange(startDate, endDate);
        }
        return leaveService.findPending();
    }

    @GetMapping({"/pending-approvals", "/pending"})
    @Operation(summary = "List pending leaves")
    public List<LeaveResponse> findPending() {
        return leaveService.findPending();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply leave")
    public LeaveResponse apply(@Valid @RequestBody LeaveRequest request) {
        return leaveService.apply(request);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve leave")
    public LeaveResponse approve(@PathVariable Long id) {
        return leaveService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject leave")
    public LeaveResponse reject(@PathVariable Long id) {
        return leaveService.reject(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete leave")
    public void delete(@PathVariable Long id) {
        leaveService.delete(id);
    }
}
