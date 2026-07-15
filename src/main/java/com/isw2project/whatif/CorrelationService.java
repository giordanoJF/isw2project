package com.isw2project.whatif;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    private static final String SPEARMAN_COL = "Spearman_with_NSmells";

    public List<Map<String, String>> computeCorrelation(Instances data, String targetAttribute) {
        return computeCorrelation(data, targetAttribute, SPEARMAN_COL);
    }

    public List<Map<String, String>> computeCorrelation(Instances data, String targetAttribute, String resultColumn) {
        Attribute attr = data.attribute(targetAttribute);
        if (attr == null) {
            throw new IllegalArgumentException("Attribute not found: " + targetAttribute);
        }
        int targetIdx = attr.index();

        double[] targetValues = extractValues(data, targetIdx);
        double[] targetRanks  = ranks(targetValues);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            if (i == targetIdx || i == data.classIndex() || !data.attribute(i).isNumeric()) {
                continue;
            }

            double[] featureValues = extractValues(data, i);
            double[] featureRanks  = ranks(featureValues);
            double spearman = pearson(targetRanks, featureRanks);

            Map<String, String> row = new LinkedHashMap<>();
            row.put("Feature", data.attribute(i).name());
            row.put(resultColumn, String.format("%.4f", spearman));
            rows.add(row);
        }

        rows.sort((a, b) -> Double.compare(
            Math.abs(Double.parseDouble(b.get(resultColumn))),
            Math.abs(Double.parseDouble(a.get(resultColumn)))
        ));

        log.info("Spearman correlation computed for {} features against {}", rows.size(), targetAttribute);
        return rows;
    }

    private double[] extractValues(Instances data, int attrIdx) {
        double[] values = new double[data.numInstances()];
        for (int i = 0; i < data.numInstances(); i++) {
            values[i] = data.instance(i).value(attrIdx);
        }
        return values;
    }

    private double[] ranks(double[] values) {
        int n = values.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        Arrays.sort(indices, Comparator.comparingDouble(i -> values[i]));

        double[] ranked = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && values[indices[j]] == values[indices[i]]) j++;
            double avgRank = (i + j - 1) / 2.0 + 1.0;
            for (int k = i; k < j; k++) ranked[indices[k]] = avgRank;
            i = j;
        }
        return ranked;
    }

    private double pearson(double[] x, double[] y) {
        int n = x.length;
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) { meanX += x[i]; meanY += y[i]; }
        meanX /= n;
        meanY /= n;

        double cov  = 0.0;
        double varX = 0.0;
        double varY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            cov  += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }
        if (varX == 0.0 || varY == 0.0) return 0.0;
        return cov / Math.sqrt(varX * varY);
    }
}
