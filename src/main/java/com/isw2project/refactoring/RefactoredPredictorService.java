package com.isw2project.refactoring;

import com.isw2project.classifier.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Utils;

import java.util.List;
import java.util.Map;

public class RefactoredPredictorService {

    private static final Logger log = LoggerFactory.getLogger(RefactoredPredictorService.class);

    private final WekaDatasetService datasetService;
    private final ClassifierBuilderService classifierBuilder;

    public RefactoredPredictorService(WekaDatasetService datasetService,
                                       ClassifierBuilderService classifierBuilder) {
        this.datasetService = datasetService;
        this.classifierBuilder = classifierBuilder;
    }

    public void annotateWithPredictions(List<Map<String, String>> rows, String inputCsvPath) throws Exception {
        Instances data = datasetService.load(inputCsvPath);

        Classifier clf = classifierBuilder.build(
            ClassifierType.RANDOM_FOREST,
            FeatureSelectionStrategy.NONE,
            BalancingStrategy.SMOTE,
            data,
            1
        );
        clf.buildClassifier(data);
        log.info("RF+NONE+SMOTE trained on {} instances from {}", data.numInstances(), inputCsvPath);

        for (Map<String, String> row : rows) {
            double[] vals = buildInstanceValues(row, data);
            DenseInstance instance = new DenseInstance(1.0, vals);
            instance.setDataset(data);

            double classIdx = clf.classifyInstance(instance);
            String predicted = data.classAttribute().value((int) classIdx);
            row.put("predicted_isBuggy", predicted);
        }

        log.info("Predictions annotated for {} rows", rows.size());
    }

    private double[] buildInstanceValues(Map<String, String> row, Instances data) {
        double[] vals = new double[data.numAttributes()];
        for (int i = 0; i < data.numAttributes(); i++) {
            if (i == data.classIndex()) {
                vals[i] = Utils.missingValue();
                continue;
            }
            String attrName = data.attribute(i).name();
            String strVal = row.getOrDefault(attrName, "0");
            try {
                vals[i] = strVal.isBlank() ? 0.0 : Double.parseDouble(strVal);
            } catch (NumberFormatException _) {
                vals[i] = 0.0;
            }
        }
        return vals;
    }
}
