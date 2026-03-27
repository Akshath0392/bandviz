package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.response.DeveloperBandwidthResponse;
import com.vymo.bandviz.service.BandwidthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/capacity", "/api/bandwidth"})
@RequiredArgsConstructor
@Tag(name = "Bandwidth", description = "Bandwidth calculations")
public class BandwidthController {

    private final BandwidthService bandwidthService;

    @GetMapping
    @Operation(summary = "Get developer bandwidth for active planning window or provided period")
    public List<DeveloperBandwidthResponse> get(
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        if (sprintId != null) {
            return bandwidthService.computeForSprint(sprintId);
        }
        if (start != null && end != null) {
            return bandwidthService.computeForPeriod(start, end);
        }
        return bandwidthService.computeForActiveWindow();
    }
}
