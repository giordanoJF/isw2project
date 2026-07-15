package com.isw2project.config;

public class RefactoringConfig {

    private String outputDir = "output/milestone4";
    private String extractedSourceDir = "output/milestone1/6_extracted_source";
    private String lastRelease = "2.0.0-beta3";
    private int smallClassLocThreshold = 100;
    private int selectionX = 2;
    private int minSmellsThreshold = 10;
    private String inputCsv = "output/milestone1/5_snapshots/OPENJPA_snapshots.csv";
    private String refactoredClassesDir = "classi_refactored";

    public String getOutputDir()                        { return outputDir; }
    public void setOutputDir(String outputDir)          { this.outputDir = outputDir; }

    public String getExtractedSourceDir()                          { return extractedSourceDir; }
    public void setExtractedSourceDir(String extractedSourceDir)   { this.extractedSourceDir = extractedSourceDir; }

    public String getLastRelease()                      { return lastRelease; }
    public void setLastRelease(String lastRelease)      { this.lastRelease = lastRelease; }

    public int getSmallClassLocThreshold()                            { return smallClassLocThreshold; }
    public void setSmallClassLocThreshold(int smallClassLocThreshold) { this.smallClassLocThreshold = smallClassLocThreshold; }

    public int getSelectionX()                  { return selectionX; }
    public void setSelectionX(int selectionX)   { this.selectionX = selectionX; }

    public int getMinSmellsThreshold()                        { return minSmellsThreshold; }
    public void setMinSmellsThreshold(int minSmellsThreshold) { this.minSmellsThreshold = minSmellsThreshold; }

    public String getInputCsv()                   { return inputCsv; }
    public void setInputCsv(String inputCsv)      { this.inputCsv = inputCsv; }

    public String getRefactoredClassesDir()                         { return refactoredClassesDir; }
    public void setRefactoredClassesDir(String refactoredClassesDir){ this.refactoredClassesDir = refactoredClassesDir; }
}
