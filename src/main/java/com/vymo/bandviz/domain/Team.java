package com.vymo.bandviz.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "team")
    @Builder.Default
    private List<Project> projects = new ArrayList<>();

    @ManyToMany(mappedBy = "permittedTeams")
    @Builder.Default
    private Set<Project> permittedProjects = new LinkedHashSet<>();

    @OneToMany(mappedBy = "team")
    @Builder.Default
    private List<Developer> developers = new ArrayList<>();
}
