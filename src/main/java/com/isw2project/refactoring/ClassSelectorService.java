package com.isw2project.refactoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClassSelectorService {

    public List<String> select(Map<String, Integer> filtered, int x) {
        List<String> ranked = new ArrayList<>(filtered.keySet());
        int size = ranked.size();
        if (size < 2 * x + 2) {
            throw new IllegalStateException(
                    "Not enough classes after filtering (" + size + ") to apply selection with X=" + x
                            + " (need at least " + (2 * x + 2) + ")");
        }
        return List.of(ranked.get(x), ranked.get(size - 1 - x));
    }
}
