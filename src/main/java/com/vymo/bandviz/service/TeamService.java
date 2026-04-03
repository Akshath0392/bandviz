package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Team;
import com.vymo.bandviz.dto.request.TeamRequest;
import com.vymo.bandviz.dto.response.JiraLinkedTeamResponse;
import com.vymo.bandviz.dto.response.TeamResponse;
import com.vymo.bandviz.exception.BusinessException;
import com.vymo.bandviz.exception.ResourceNotFoundException;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.ProjectRepository;
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
    private final ProjectRepository projectRepository;
    private final DeveloperRepository developerRepository;

    public List<TeamResponse> findAll(boolean activeOnly) {
        List<Team> teams = activeOnly ? teamRepository.findAllByActiveTrue() : teamRepository.findAll();
        return teams.stream().map(this::toResponse).toList();
    }

    public TeamResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public List<JiraLinkedTeamResponse> findJiraLinkedTeams() {
        return teamRepository.findAllByActiveTrue().stream()
                .map(team -> {
                    List<JiraLinkedTeamResponse.ProjectLink> links = projectRepository.findDistinctByActiveTrueAndPermittedTeams_Id(team.getId()).stream()
                            .filter(project -> project.getJiraProjectKey() != null && !project.getJiraProjectKey().isBlank())
                            .map(project -> {
                                JiraLinkedTeamResponse.ProjectLink link = new JiraLinkedTeamResponse.ProjectLink();
                                link.setProjectId(project.getId());
                                link.setProjectName(project.getName());
                                link.setJiraProjectKey(project.getJiraProjectKey());
                                return link;
                            })
                            .toList();
                    if (links.isEmpty()) {
                        return null;
                    }
                    JiraLinkedTeamResponse response = new JiraLinkedTeamResponse();
                    response.setTeamId(team.getId());
                    response.setTeamName(team.getName());
                    response.setProjects(links);
                    return response;
                })
                .filter(item -> item != null)
                .toList();
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
        response.setProjectCount((int) projectRepository.countByActiveTrueAndPermittedTeams_Id(team.getId()));
        response.setDeveloperCount((int) developerRepository.countByActiveTrueAndTeamId(team.getId()));
        return response;
    }
}
