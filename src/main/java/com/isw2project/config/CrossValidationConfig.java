package com.isw2project.config;

public class CrossValidationConfig {

    private int runs = 10;
    private int folds = 10;

    public int getRuns()          { return runs; }
    public void setRuns(int runs) { this.runs = runs; }

    public int getFolds()           { return folds; }
    public void setFolds(int folds) { this.folds = folds; }
}