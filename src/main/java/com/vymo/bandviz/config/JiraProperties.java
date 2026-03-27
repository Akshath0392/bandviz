package com.vymo.bandviz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "bandviz.jira")
@Data
public class JiraProperties {
    private String baseUrl;
    private String email;
    private String apiToken;
    private boolean syncEnabled;
    private List<String> projectKeys = new ArrayList<>();
}
