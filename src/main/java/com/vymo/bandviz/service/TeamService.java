package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.dto.request.TeamRequest;
import com.vymo.bandviz.dto.response.TeamResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;

    public List<TeamResponse> findAll(boolean activeOnly) {
        List<Team> teams = activeOnly ? teamRepository.findAllByActiveTrue() : teamRepository.findAll();
        return teams.stream().map(this::toResponse).toList();
    }

    public TeamResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public TeamResponse create(TeamRequest request) {
        if (teamRepository.existsByName(request.getName())) {
            throw new BusinessException("A team named '" + request.getName() + "' already exists");
        }
        Team team = Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build();
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public TeamResponse update(Long id, TeamRequest request) {
        Team team = getOrThrow(id);
        if (teamRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new BusinessException("A team named '" + request.getName() + "' already exists");
        }
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public void deactivate(Long id) {
        Team team = getOrThrow(id);
        team.setActive(false);
        teamRepository.save(team);
    }

    Team getOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + id));
    }

    private TeamResponse toResponse(Team team) {
        TeamResponse response = new TeamResponse();
        response.setId(team.getId());
        response.setName(team.getName());
        response.setDescription(team.getDescription());
        response.setActive(team.getActive());
        response.setProjectCount((int) team.getProjects().stream().filter(project -> Boolean.TRUE.equals(project.getActive())).count());
        response.setDeveloperCount((int) team.getDevelopers().stream().filter(developer -> Boolean.TRUE.equals(developer.getActive())).count());
        return response;
    }
}
