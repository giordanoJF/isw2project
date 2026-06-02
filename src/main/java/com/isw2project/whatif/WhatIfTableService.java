package com.isw2project.whatif;

import weka.core.Instances;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhatIfTableService {

    public List<Map<String, String>> buildDatasetRows(WhatIfDatasetService.Datasets datasets,
                                                       WhatIfPredictorService.Predictions preds) {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(datasetRow("A",  datasets.a(),     preds.a()));
        rows.add(datasetRow("B+", datasets.bPlus(),  preds.bPlus()));
        rows.add(datasetRow("B",  datasets.b(),      preds.b()));
        rows.add(datasetRow("C",  datasets.c(),      preds.c()));
        return rows;
    }

    public List<Map<String, String>> buildPreventionRows(WhatIfPredictorService.Predictions preds,
                                                          int actualBuggyA) {
        boolean[] predBplus = preds.bPlus();
        boolean[] predB     = preds.b();

        int prevented      = 0;
        int predictedBplus = 0;
        for (int i = 0; i < predBplus.length; i++) {
            if (predBplus[i]) predictedBplus++;
            if (predBplus[i] && !predB[i]) prevented++;
        }

        double proportion     = actualBuggyA > 0        ? (double) prevented / actualBuggyA   : 0.0;
        double ofPredictable  = predictedBplus > 0      ? (double) prevented / predictedBplus : 0.0;

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(preventionRow("Prevented_Total",          String.valueOf(prevented)));
        rows.add(preventionRow("Prevented_Proportion",     format(proportion)));
        rows.add(preventionRow("Prevented_Of_Predictable", format(ofPredictable)));
        return rows;
    }

    private Map<String, String> datasetRow(String name, Instances data, boolean[] pred) {
        int posIdx       = data.classAttribute().indexOfValue("true");
        int actualBuggy  = 0;
        int predictedBuggy = 0;
        for (int i = 0; i < data.numInstances(); i++) {
            if ((int) data.instance(i).classValue() == posIdx) actualBuggy++;
            if (pred[i]) predictedBuggy++;
        }
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Dataset",         name);
        row.put("Instances",       String.valueOf(data.numInstances()));
        row.put("Actual_Buggy",    String.valueOf(actualBuggy));
        row.put("Predicted_Buggy", String.valueOf(predictedBuggy));
        return row;
    }

    private Map<String, String> preventionRow(String metric, String value) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Metric", metric);
        row.put("Value",  value);
        return row;
    }

    private String format(double v) {
        return String.format("%.4f", v);
    }

    public int countActualBuggy(Instances data) {
        int posIdx = data.classAttribute().indexOfValue("true");
        int count  = 0;
        for (int i = 0; i < data.numInstances(); i++) {
            if ((int) data.instance(i).classValue() == posIdx) count++;
        }
        return count;
    }
}
