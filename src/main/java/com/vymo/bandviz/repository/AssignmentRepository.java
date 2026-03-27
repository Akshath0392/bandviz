package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findAllByDeveloperId(Long developerId);

    List<Assignment> findAllByProjectId(Long projectId);

    // Active assignments for a developer on a given date
    @Query("""
        SELECT a FROM Assignment a
        WHERE a.developer.id = :developerId
          AND a.startDate <= :date
          AND (a.endDate IS NULL OR a.endDate >= :date)
        """)
    List<Assignment> findActiveByDeveloperIdAndDate(
            @Param("developerId") Long developerId,
            @Param("date") LocalDate date);

    // All active assignments across all developers on a given date
    @Query("""
        SELECT a FROM Assignment a
        JOIN FETCH a.developer
        JOIN FETCH a.project
        WHERE a.startDate <= :date
          AND (a.endDate IS NULL OR a.endDate >= :date)
          AND a.developer.active = true
        """)
    List<Assignment> findAllActiveOnDate(@Param("date") LocalDate date);
}
