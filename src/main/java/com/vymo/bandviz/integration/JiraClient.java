package com.vymo.bandviz.integration;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.integration.dto.JiraSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JiraClient {

    private final JiraProperties jiraProperties;
    private final RestTemplate restTemplate;

    private static final int PAGE_SIZE = 100;

    public JiraSearchResponse fetchOpenIssues(String projectKey, String nextPageToken) {
        String jql = String.format(
                "project = %s AND statusCategory != Done ORDER BY updated DESC", projectKey);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(jiraProperties.getBaseUrl() + "/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("maxResults", PAGE_SIZE)
                .queryParam(
                        "fields",
                        String.join(",", List.of("summary", "assignee", "status", "priority", "customfield_10016", "customfield_10020"))
                );

        if (nextPageToken != null && !nextPageToken.isBlank()) {
            builder.queryParam("nextPageToken", nextPageToken);
        }

        URI uri = builder.build().encode().toUri();

        log.info("Fetching Jira issues for project {} using enhanced search API. JQL: {}", projectKey, jql);
        log.debug("Calling Jira endpoint {} with nextPageToken={}", uri, nextPageToken);

        try {
            ResponseEntity<JiraSearchResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    JiraSearchResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException(buildFailureMessage(
                        projectKey,
                        response.getStatusCode(),
                        response.getHeaders(),
                        "Non-success Jira response received"
                ));
            }

            JiraSearchResponse body = response.getBody();
            if (body == null) {
                throw new IllegalStateException(buildFailureMessage(
                        projectKey,
                        response.getStatusCode(),
                        response.getHeaders(),
                        "Jira returned an empty response body"
                ));
            }

            log.info("JIRA Response body {}", body);
            int issueCount = body.getIssues() != null ? body.getIssues().size() : 0;
            log.info(
                    "Jira search response for project {} returned {} issues, isLast={}, nextPageToken={}",
                    projectKey,
                    issueCount,
                    body.isLast(),
                    body.getNextPageToken()
            );

            return body;
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String message = buildFailureMessage(
                    projectKey,
                    HttpStatusCode.valueOf(ex.getRawStatusCode()),
                    ex.getResponseHeaders(),
                    responseBody == null || responseBody.isBlank() ? ex.getStatusText() : responseBody
            );
            log.error(message);
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            String message = String.format(
                    "Jira request failed for project %s due to connectivity or timeout issue: %s",
                    projectKey,
                    ex.getMessage()
            );
            log.error(message);
            throw new IllegalStateException(message, ex);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String credentials = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String buildFailureMessage(
            String projectKey,
            HttpStatusCode statusCode,
            HttpHeaders headers,
            String details
    ) {
        String headerSummary = headers == null || headers.isEmpty()
                ? "no response headers"
                : headers.entrySet().stream()
                .filter(entry -> !"Set-Cookie".equalsIgnoreCase(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("; "));

        String statusHint = switch (statusCode.value()) {
            case 401 -> "Authentication failed. Verify JIRA_EMAIL and JIRA_API_TOKEN.";
            case 403 -> "Authorization failed. The Jira user may not have permission to access this project.";
            case 429 -> "Jira rate limit reached. Retry later or reduce sync frequency.";
            default -> "Unexpected Jira API failure.";
        };

        return String.format(
                "Jira API request failed for project %s. HTTP %s. %s Details: %s. Headers: %s",
                projectKey,
                statusCode,
                statusHint,
                details,
                headerSummary
        );
    }
}
