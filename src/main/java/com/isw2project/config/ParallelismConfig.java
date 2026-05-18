package com.isw2project.config;

/**
 * CPU parallelism settings for the Milestone 2 classifier evaluation.
 *
 * Two independent levels of parallelism are available:
 *   1. combination-level: each (classifier x FS x balancing) combination runs on a
 *      separate thread. Fully independent, scales linearly up to the combination count.
 *   2. RandomForest-internal: trees are built in parallel via setNumExecutionSlots().
 *      Only affects RandomForest; NaiveBayes and IBk are always single-threaded.
 *
 * Both levels share the same physical CPU cores; their product should not exceed
 * Runtime.getRuntime().availableProcessors() to avoid oversubscription.
 *
 * Values accept three formats:
 *   "auto"  — automatic (combinations = all cores; rfSlots = cores / combinations)
 *   "8"     — explicit thread count
 *   "50%"   — percentage of available CPU cores (rounded, minimum 1)
 */
public class ParallelismConfig {

    private boolean interactive = false;
    private String combinations = "auto";
    private String randomForestSlots = "auto";

    public boolean isInteractive()                  { return interactive; }
    public void setInteractive(boolean interactive) { this.interactive = interactive; }

    public String getCombinations()                 { return combinations; }
    public void setCombinations(String c)           { this.combinations = c; }

    public String getRandomForestSlots()            { return randomForestSlots; }
    public void setRandomForestSlots(String s)      { this.randomForestSlots = s; }

    public int resolvedCombinationThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return resolve(combinations, cores, cores);
    }

    public int resolvedRandomForestSlots() {
        int cores = Runtime.getRuntime().availableProcessors();
        int autoValue = Math.max(1, cores / resolvedCombinationThreads());
        return resolve(randomForestSlots, cores, autoValue);
    }

    /**
     * Parses a config value into a thread count.
     *
     * @param value     raw config string ("auto", "8", "50%", "0", null)
     * @param base      the denominator for percentage calculations (available cores)
     * @param autoValue the value to use when the string means "auto"
     */
    public static int resolve(String value, int base, int autoValue) {
        if (isAuto(value)) return autoValue;
        String trimmed = value.trim();
        if (trimmed.endsWith("%")) {
            double pct = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
            return Math.max(1, (int) Math.round(base * pct / 100.0));
        }
        return Math.max(1, Integer.parseInt(trimmed));
    }

    public static boolean isAuto(String value) {
        return value == null || value.isBlank()
                || value.trim().equals("0")
                || value.trim().equalsIgnoreCase("auto");
    }
}
