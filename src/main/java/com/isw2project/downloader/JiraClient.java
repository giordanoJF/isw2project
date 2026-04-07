package com.isw2project.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Low-level HTTP client for the Jira REST API.
 * Responsible only for making requests and returning raw JSON nodes.
 */
public class JiraClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JiraClient(String baseUrl) {
        this.baseUrl      = baseUrl.replaceAll("/$", ""); // strip trailing slash
        this.httpClient   = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches issues for a project using a JQL query with pagination support.
     *
     * @param jql        the JQL query string
     * @param startAt    pagination offset
     * @param maxResults page size
     * @return raw JSON response node
     */
    public JsonNode searchIssues(String jql, int startAt, int maxResults) {
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
        String url = String.format("%s/rest/api/2/search?jql=%s&startAt=%d&maxResults=%d",
                baseUrl, encodedJql, startAt, maxResults);
        return get(url);
    }

    /**
     * Fetches all versions for a given project key.
     *
     * @param projectKey the Jira project key (e.g. "KAFKA")
     * @return raw JSON array node
     */
    public JsonNode getVersions(String projectKey) {
        String url = String.format("%s/rest/api/2/project/%s/versions", baseUrl, projectKey);
        return get(url);
    }

    // -------------------------------------------------------------------------

    private JsonNode get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Jira API error %d for URL: %s".formatted(response.statusCode(), url));
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("HTTP request failed: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted: " + url, e);
        }
    }
}