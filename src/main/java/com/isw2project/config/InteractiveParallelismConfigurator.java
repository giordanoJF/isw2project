package com.isw2project.config;

import java.util.Scanner;

/**
 * Prompts the user at runtime to choose parallelism settings.
 *
 * Invoked only when config.yaml has parallelism.interactive = true.
 * Displays available hardware info, accepts integer or percentage input,
 * validates bounds, and returns a ParallelismConfig with explicit resolved values.
 */
@SuppressWarnings("java:S106") // intentional System.out: interactive console prompt
public class InteractiveParallelismConfigurator {

    private InteractiveParallelismConfigurator() {}

    /**
     * @param totalCombinations number of combinations derived from the config lists
     * @return a ParallelismConfig with explicit string values (never "auto")
     */
    public static ParallelismConfig configure(int totalCombinations) {
        int cores = Runtime.getRuntime().availableProcessors();
        int maxCombinationThreads = Math.min(cores, totalCombinations);

        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("=== Configurazione Parallelismo ===");
        System.out.printf("Core disponibili: %d | Combinazioni totali: %d%n", cores, totalCombinations);
        System.out.println();

        int combinationThreads = promptCombinations(scanner, maxCombinationThreads);
        int rfSlots            = promptRfSlots(scanner, cores, combinationThreads);

        printSummary(combinationThreads, rfSlots, cores);

        ParallelismConfig resolved = new ParallelismConfig();
        resolved.setCombinations(String.valueOf(combinationThreads));
        resolved.setRandomForestSlots(String.valueOf(rfSlots));
        return resolved;
    }

    private static int promptCombinations(Scanner scanner, int max) {
        int defaultValue = max;
        System.out.println("[1/2] Thread per le combinazioni");
        System.out.println("      Quante combinazioni eseguire in parallelo.");
        System.out.println("      Accetta: numero intero (es. \"4\") o percentuale dei core (es. \"50%\")");
        System.out.printf("      Default: %d (tutti i core utili) | Range: [1-%d]%n", defaultValue, max);
        return prompt(scanner, max, defaultValue);
    }

    private static int promptRfSlots(Scanner scanner, int cores, int combinationThreads) {
        int defaultValue = Math.max(1, cores / combinationThreads);
        int max          = cores;
        System.out.println();
        System.out.println("[2/2] Thread interni RandomForest");
        System.out.println("      Quanti thread usa RandomForest per costruire gli alberi in parallelo.");
        System.out.println("      Non influisce su NaiveBayes e IBk (sempre single-thread).");
        System.out.println("      Accetta: numero intero (es. \"2\") o percentuale dei core (es. \"25%\")");
        System.out.printf("      Default: %d (auto = %d core / %d thread) | Range: [1-%d]%n",
                defaultValue, cores, combinationThreads, max);
        return prompt(scanner, max, defaultValue);
    }

    private static void printSummary(int combinationThreads, int rfSlots, int cores) {
        int used = combinationThreads * rfSlots;
        System.out.println();
        System.out.printf("Riepilogo: %d thread combinazioni × %d slot RF = %d core usati su %d disponibili",
                combinationThreads, rfSlots, used, cores);
        if (used > cores) {
            System.out.print("  [ATTENZIONE: supera i core disponibili, possibile oversubscription]");
        }
        System.out.println();
        System.out.println();
    }

    private static int prompt(Scanner scanner, int max, int defaultValue) {
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) return defaultValue;

            try {
                int value = ParallelismConfig.resolve(line, Runtime.getRuntime().availableProcessors(), defaultValue);
                if (value < 1 || value > max) {
                    System.out.printf("  Valore non valido: deve essere tra 1 e %d (hai inserito %d).%n", max, value);
                    continue;
                }
                return value;
            } catch (NumberFormatException _) {
                System.out.println("  Formato non riconosciuto: inserire un intero (es. \"4\") o una percentuale (es. \"50%\").");
            }
        }
    }
}
