package com.isw2project.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;

/**
 * Loads the Milestone 1 snapshot CSV into a Weka Instances object.
 *
 * Non-feature columns (java_class_path, release) are removed before returning.
 * The class attribute is set to "isBuggy" (nominal: false / true).
 */
public class WekaDatasetService {

    private static final Logger log = LoggerFactory.getLogger(WekaDatasetService.class);

    private static final String CLASS_ATTRIBUTE = "isBuggy";
    private static final String[] NON_FEATURE_COLUMNS = {"java_class_path", "release"};

    public Instances load(String csvPath) throws Exception {
        log.info("Loading dataset from: {}", csvPath);
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();
        log.info("Loaded {} instances, {} attributes", data.numInstances(), data.numAttributes());

        data = removeNonFeatureColumns(data);
        setClassAttribute(data);

        log.info("Dataset ready: {} instances, {} features + class",
                data.numInstances(), data.numAttributes() - 1);
        return data;
    }

    private Instances removeNonFeatureColumns(Instances data) throws Exception {
        StringBuilder indices = new StringBuilder();
        for (String colName : NON_FEATURE_COLUMNS) {
            int idx = data.attribute(colName) != null ? data.attribute(colName).index() + 1 : -1;
            if (idx > 0) {
                if (!indices.isEmpty()) {
                    indices.append(",");
                }
                indices.append(idx);
            }
        }
        if (indices.isEmpty()) {
            return data;
        }
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

    /**
     * Returns the index of the positive class value ("true" = buggy) within
     * the class attribute's nominal values. Used by evaluation services to
     * target the correct class in Precision, Recall, AUC, and NPofB20.
     */
    public int getPositiveClassIndex(Instances data) {
        int idx = data.classAttribute().indexOfValue("true");
        if (idx < 0) {
            log.warn("Positive class value 'true' not found; defaulting to index 1");
            return 1;
        }
        return idx;
    }
}
