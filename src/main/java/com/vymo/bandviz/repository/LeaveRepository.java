package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.Leave;
import com.vymo.bandviz.domain.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findAllByDeveloperId(Long developerId);

    List<Leave> findAllByStatus(LeaveStatus status);

    // Approved leaves for a developer that overlap with a given date range
    @Query("""
        SELECT l FROM Leave l
        WHERE l.developer.id = :developerId
          AND l.status = 'APPROVED'
          AND l.startDate <= :endDate
          AND l.endDate >= :startDate
        """)
    List<Leave> findApprovedLeavesInRange(
            @Param("developerId") Long developerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // All approved leaves for any developer overlapping with a range
    @Query("""
        SELECT l FROM Leave l
        JOIN FETCH l.developer
        WHERE l.status = 'APPROVED'
          AND l.startDate <= :endDate
          AND l.endDate >= :startDate
        """)
    List<Leave> findAllApprovedLeavesInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
