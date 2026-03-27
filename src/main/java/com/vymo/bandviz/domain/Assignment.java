package com.vymo.bandviz.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "assignments",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_assignment_dev_project_dates",
        columnNames = {"developer_id", "project_id", "start_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "developer_id", nullable = false)
    private Developer developer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // Percentage of the developer's time allocated to this project (0-100)
    @Column(nullable = false)
    private Integer allocationPct;

    @Column(nullable = false)
    private LocalDate startDate;

    // Null means open-ended / ongoing
    private LocalDate endDate;
}
