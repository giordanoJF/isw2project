package com.isw2project.config;

import java.util.Map;

public class CsvConfig {

    private String outputDir;
    private Map<String, Boolean> issueColumns;
    private Map<String, Boolean> versionColumns;

    public String getOutputDir()                    { return outputDir; }
    public void setOutputDir(String outputDir)      { this.outputDir = outputDir; }

    public Map<String, Boolean> getIssueColumns()                       { return issueColumns; }
    public void setIssueColumns(Map<String, Boolean> issueColumns)      { this.issueColumns = issueColumns; }

    public Map<String, Boolean> getVersionColumns()                         { return versionColumns; }
    public void setVersionColumns(Map<String, Boolean> versionColumns)      { this.versionColumns = versionColumns; }
}