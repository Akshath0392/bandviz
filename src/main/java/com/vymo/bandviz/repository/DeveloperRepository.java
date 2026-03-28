package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.Developer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeveloperRepository extends JpaRepository<Developer, Long> {

    List<Developer> findAllByActiveTrue();

    List<Developer> findAllByActiveTrueAndTeamId(Long teamId);

    List<Developer> findAllByTeamId(Long teamId);

    Optional<Developer> findByEmail(String email);

    Optional<Developer> findByJiraUsername(String jiraUsername);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}
