package com.vymo.bandviz.repository;

import com.vymo.bandviz.domain.JiraSyncFilter;
import com.vymo.bandviz.domain.enums.JiraFilterScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JiraSyncFilterRepository extends JpaRepository<JiraSyncFilter, Long> {

    Optional<JiraSyncFilter> findByScopeAndProjectKey(JiraFilterScope scope, String projectKey);

    List<JiraSyncFilter> findAllByScopeOrderByProjectKeyAsc(JiraFilterScope scope);

    void deleteByScopeAndProjectKey(JiraFilterScope scope, String projectKey);
}
