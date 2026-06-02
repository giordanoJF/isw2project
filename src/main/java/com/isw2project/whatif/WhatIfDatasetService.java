package com.isw2project.whatif;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;

public class WhatIfDatasetService {

    private static final Logger log = LoggerFactory.getLogger(WhatIfDatasetService.class);

    private static final String CLASS_ATTRIBUTE = "isBuggy";
    private static final String[] NON_FEATURE_COLUMNS = {"java_class_path", "release"};

    public record Datasets(Instances a, Instances bPlus, Instances b, Instances c) {}

    public Datasets loadAndSplit(String csvPath, String smellsColumn) throws Exception {
        log.info("Loading dataset from: {}", csvPath);
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances raw = loader.getDataSet();
        log.info("Loaded {} instances, {} attributes", raw.numInstances(), raw.numAttributes());

        int smellsIdx = raw.attribute(smellsColumn).index();

        Instances bPlusRaw = new Instances(raw, 0);
        Instances cRaw     = new Instances(raw, 0);
        for (int i = 0; i < raw.numInstances(); i++) {
            Instance inst = raw.instance(i);
            if (inst.value(smellsIdx) > 0) {
                bPlusRaw.add(inst);
            } else {
                cRaw.add(inst);
            }
        }

        // B = copy of B+ with smells zeroed (counterfactual)
        Instances bRaw = new Instances(bPlusRaw);
        for (int i = 0; i < bRaw.numInstances(); i++) {
            bRaw.instance(i).setValue(smellsIdx, 0.0);
        }

        log.info("Split: A={}, B+={}, C={}", raw.numInstances(), bPlusRaw.numInstances(), cRaw.numInstances());

        Instances a     = prepare(raw);
        Instances bPlus = prepare(bPlusRaw);
        Instances b     = prepare(bRaw);
        Instances c     = prepare(cRaw);

        return new Datasets(a, bPlus, b, c);
    }

    private Instances prepare(Instances data) throws Exception {
        Instances cleaned = removeNonFeatureColumns(data);
        setClassAttribute(cleaned);
        return cleaned;
    }

    private Instances removeNonFeatureColumns(Instances data) throws Exception {
        StringBuilder indices = new StringBuilder();
        for (String colName : NON_FEATURE_COLUMNS) {
            if (data.attribute(colName) != null) {
                int idx = data.attribute(colName).index() + 1;
                if (!indices.isEmpty()) indices.append(",");
                indices.append(idx);
            }
        }
        if (indices.isEmpty()) return data;
        Remove remove = new Remove();
        remove.setAttributeIndices(indices.toString());
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }

    private void setClassAttribute(Instances data) {
        int classIdx = data.attribute(CLASS_ATTRIBUTE) != null
                ? data.attribute(CLASS_ATTRIBUTE).index()
                : data.numAttributes() - 1;
        data.setClassIndex(classIdx);
    }

    public int getPositiveClassIndex(Instances data) {
        int idx = data.classAttribute().indexOfValue("true");
        if (idx < 0) {
            log.warn("Positive class value 'true' not found; defaulting to index 1");
            return 1;
        }
        return idx;
    }
}
