package com.vymo.bandviz.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueResponse {

    private String id;
    private String key;
    private Fields fields;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        private String summary;
        private Assignee assignee;
        private Status status;
        private Priority priority;
        private Integer storyPoints;
        private List<Sprint> customfield_10020; // Sprint field

        @JsonProperty("story_points")
        private Integer customfield_10016;       // Story points custom field

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Assignee {
            private String accountId;
            private String displayName;
            private String emailAddress;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Status {
            private String name;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Priority {
            private String name;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Sprint {
            private Long id;
            private String name;
            private String state;
        }
    }
}
