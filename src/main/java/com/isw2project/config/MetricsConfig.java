package com.isw2project.config;

/**
 * Configuration for Milestone 1 metrics computation (config.yaml section: metrics).
 */
public class MetricsConfig {

    private double snapshotPercentage = 1.0;
    private PmdConfig pmd = new PmdConfig();

    public double getSnapshotPercentage() { return snapshotPercentage; }
    public void setSnapshotPercentage(double snapshotPercentage) { this.snapshotPercentage = snapshotPercentage; }

    public PmdConfig getPmd() { return pmd; }
    public void setPmd(PmdConfig pmd) { this.pmd = pmd; }

    public static class PmdConfig {

        private int batchSize = 100;
        private double cpuFraction = 0.5;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public double getCpuFraction() { return cpuFraction; }
        public void setCpuFraction(double cpuFraction) { this.cpuFraction = cpuFraction; }
    }
}
