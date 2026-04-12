package com.isw2project.csv;

import com.isw2project.config.CsvConfig;
import com.isw2project.model.ProjectData;

import java.util.List;
import java.util.Map;

public class CsvExporterOrchestrator {

    private final CsvConfig config;
    private final IssueCsvRowMapperService issueMapper;
    private final VersionCsvRowMapperService versionMapper;
    private final CsvWriterService writerService;

    public CsvExporterOrchestrator(CsvConfig config,
                                   IssueCsvRowMapperService issueMapper,
                                   VersionCsvRowMapperService versionMapper,
                                   CsvWriterService writerService) {
        this.config         = config;
        this.issueMapper    = issueMapper;
        this.versionMapper  = versionMapper;
        this.writerService  = writerService;
    }

    public void export(List<ProjectData> projects, String subDir) {
        projects.forEach(project -> exportProject(project, subDir));
    }

    private void exportProject(ProjectData projectData, String subDir) {
        exportIssues(projectData, subDir);
        exportVersions(projectData, subDir);
    }

    private void exportIssues(ProjectData projectData, String subDir) {
        Map<String, Boolean> columns = config.getIssueColumns();
        List<Map<String, String>> rows = projectData.getIssues().stream()
                .map(issue -> issueMapper.map(issue, columns))
                .toList();

        writerService.write(config.getOutputDir() + "/" + subDir,
                projectData.getProjectKey() + "_issues.csv", rows);
    }

    private void exportVersions(ProjectData projectData, String subDir) {
        Map<String, Boolean> columns = config.getVersionColumns();
        List<Map<String, String>> rows = projectData.getVersions().stream()
                .map(version -> versionMapper.map(version, columns))
                .toList();

        writerService.write(config.getOutputDir() + "/" + subDir,
                projectData.getProjectKey() + "_versions.csv", rows);
    }
}