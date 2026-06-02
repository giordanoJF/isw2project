package com.isw2project.config;

public class WhatIfConfig {

    private String inputCsv;
    private String outputDir;
    private String datasetName;
    private String bestClassifier;
    private String bestFs;
    private String bestBalancing;
    private String smellsColumn;

    public String getInputCsv()                 { return inputCsv; }
    public void setInputCsv(String inputCsv)     { this.inputCsv = inputCsv; }

    public String getOutputDir()                 { return outputDir; }
    public void setOutputDir(String outputDir)   { this.outputDir = outputDir; }

    public String getDatasetName()                   { return datasetName; }
    public void setDatasetName(String datasetName)   { this.datasetName = datasetName; }

    public String getBestClassifier()                    { return bestClassifier; }
    public void setBestClassifier(String bestClassifier) { this.bestClassifier = bestClassifier; }

    public String getBestFs()              { return bestFs; }
    public void setBestFs(String bestFs)   { this.bestFs = bestFs; }

    public String getBestBalancing()                   { return bestBalancing; }
    public void setBestBalancing(String bestBalancing) { this.bestBalancing = bestBalancing; }

    public String getSmellsColumn()                    { return smellsColumn; }
    public void setSmellsColumn(String smellsColumn)   { this.smellsColumn = smellsColumn; }
}
