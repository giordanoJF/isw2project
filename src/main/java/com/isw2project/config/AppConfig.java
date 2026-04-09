package com.isw2project.config;

import java.util.List;

/**
 * Root configuration object mapped from config.yaml.
 */
public class AppConfig {

    private String baseUrl;
    private List<ProjectConfig> projects;
    private CsvConfig csv;

    public CsvConfig getCsv()           { return csv; }
    public void setCsv(CsvConfig csv)   { this.csv = csv; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public List<ProjectConfig> getProjects() { return projects; }
    public void setProjects(List<ProjectConfig> projects) { this.projects = projects; }
}