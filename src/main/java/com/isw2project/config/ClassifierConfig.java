package com.isw2project.config;

import java.util.List;

public class ClassifierConfig {

    private String inputCsv;
    private String outputDir;
    private String datasetName;
    private List<String> classifiers;
    private List<String> featureSelection;
    private List<String> balancing;
    private CrossValidationConfig crossValidation;
    private String wrapperBaseClassifier;
    private ParallelismConfig parallelism = new ParallelismConfig();

    public String getInputCsv()                   { return inputCsv; }
    public void setInputCsv(String inputCsv)       { this.inputCsv = inputCsv; }

    public String getOutputDir()                   { return outputDir; }
    public void setOutputDir(String outputDir)     { this.outputDir = outputDir; }

    public String getDatasetName()                 { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public List<String> getClassifiers()                   { return classifiers; }
    public void setClassifiers(List<String> classifiers)   { this.classifiers = classifiers; }

    public List<String> getFeatureSelection()                      { return featureSelection; }
    public void setFeatureSelection(List<String> featureSelection) { this.featureSelection = featureSelection; }

    public List<String> getBalancing()                 { return balancing; }
    public void setBalancing(List<String> balancing)   { this.balancing = balancing; }

    public CrossValidationConfig getCrossValidation()                        { return crossValidation; }
    public void setCrossValidation(CrossValidationConfig crossValidation)    { this.crossValidation = crossValidation; }

    public String getWrapperBaseClassifier()                           { return wrapperBaseClassifier; }
    public void setWrapperBaseClassifier(String wrapperBaseClassifier) { this.wrapperBaseClassifier = wrapperBaseClassifier; }

    public ParallelismConfig getParallelism()                      { return parallelism; }
    public void setParallelism(ParallelismConfig parallelism)      { this.parallelism = parallelism; }
}