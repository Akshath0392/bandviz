package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.dto.request.DeveloperRequest;
import com.vymo.bandviz.dto.response.DeveloperResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperService {

    private final DeveloperRepository developerRepository;
    private final TeamRepository teamRepository;

    public List<DeveloperResponse> findAll(boolean activeOnly, Long teamId) {
        List<Developer> developers;
        if (teamId != null) {
            developers = activeOnly
                    ? developerRepository.findAllByActiveTrueAndTeamId(teamId)
                    : developerRepository.findAllByTeamId(teamId);
        } else {
            developers = activeOnly
                    ? developerRepository.findAllByActiveTrue()
                    : developerRepository.findAll();
        }
        return developers.stream().map(this::toResponse).toList();
    }

    public DeveloperResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public DeveloperResponse create(DeveloperRequest request) {
        if (developerRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("A developer with email " + request.getEmail() + " already exists");
        }
        Developer developer = Developer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .role(request.getRole())
                .weeklyCapacityHours(request.getWeeklyCapacityHours())
                .jiraUsername(request.getJiraUsername())
                .team(resolveTeam(request.getTeamId()))
                .active(true)
                .build();
        return toResponse(developerRepository.save(developer));
    }

    @Transactional
    public DeveloperResponse update(Long id, DeveloperRequest request) {
        Developer developer = getOrThrow(id);
        if (developerRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new BusinessException("A developer with email " + request.getEmail() + " already exists");
        }
        developer.setName(request.getName());
        developer.setEmail(request.getEmail());
        developer.setRole(request.getRole());
        developer.setWeeklyCapacityHours(request.getWeeklyCapacityHours());
        developer.setJiraUsername(request.getJiraUsername());
        developer.setTeam(resolveTeam(request.getTeamId()));
        return toResponse(developerRepository.save(developer));
    }

    @Transactional
    public void deactivate(Long id) {
        Developer developer = getOrThrow(id);
        developer.setActive(false);
        developerRepository.save(developer);
    }

    Developer getOrThrow(Long id) {
        return developerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Developer not found: " + id));
    }

    private DeveloperResponse toResponse(Developer d) {
        DeveloperResponse r = new DeveloperResponse();
        r.setId(d.getId());
        r.setName(d.getName());
        r.setEmail(d.getEmail());
        r.setRole(d.getRole());
        r.setWeeklyCapacityHours(d.getWeeklyCapacityHours());
        r.setJiraUsername(d.getJiraUsername());
        r.setTeamId(d.getTeam() != null ? d.getTeam().getId() : null);
        r.setTeamName(d.getTeam() != null ? d.getTeam().getName() : null);
        r.setActive(d.getActive());
        return r;
    }

    private Team resolveTeam(Long teamId) {
        if (teamId == null) {
            throw new BusinessException("teamId is required for developers");
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }
}
