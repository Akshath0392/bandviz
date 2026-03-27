package com.vymo.bandviz.domain;

import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(unique = true)
    private String jiraProjectKey;

    // Hex color for UI display e.g. "#6366f1"
    private String color;

    // Target max utilization % for this project (default 70)
    @Column(nullable = false)
    @Builder.Default
    private Integer targetUtilizationPct = 70;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectDeliveryMode deliveryMode = ProjectDeliveryMode.HYBRID;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();
}
