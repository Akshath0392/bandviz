package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    Optional<Sprint> findByActiveTrue();

    @Query("SELECT s FROM Sprint s WHERE :date BETWEEN s.startDate AND s.endDate")
    Optional<Sprint> findByDate(@Param("date") LocalDate date);

    Optional<Sprint> findByName(String name);
}
