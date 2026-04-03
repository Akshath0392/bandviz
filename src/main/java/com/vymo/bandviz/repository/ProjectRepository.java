package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByActiveTrue();

    List<Project> findDistinctByActiveTrueAndPermittedTeams_Id(Long teamId);

    List<Project> findDistinctByPermittedTeams_Id(Long teamId);

    long countByActiveTrueAndPermittedTeams_Id(Long teamId);

    Optional<Project> findByJiraProjectKey(String jiraProjectKey);

    boolean existsByName(String name);
}
