package com.isw2project.whatif;

import com.isw2project.classifier.BalancingStrategy;
import com.isw2project.classifier.ClassifierBuilderService;
import com.isw2project.classifier.ClassifierEvaluatorService;
import com.isw2project.classifier.ClassifierType;
import com.isw2project.classifier.FeatureSelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AblationService {

    private static final Logger log = LoggerFactory.getLogger(AblationService.class);

    private static final int RUNS  = 10;
    private static final int FOLDS = 10;

    private final ClassifierBuilderService   classifierBuilder;
    private final ClassifierEvaluatorService evaluator;

    public AblationService(ClassifierBuilderService classifierBuilder,
                            ClassifierEvaluatorService evaluator) {
        this.classifierBuilder = classifierBuilder;
        this.evaluator         = evaluator;
    }

    public List<Map<String, String>> runAblation(Instances data, String smellsColumn) throws Exception {
        int posIdx = data.classAttribute().indexOfValue("true");

        log.info("Ablation (1/2): RF + SMOTE with all {} features — estimated ~25 min",
                data.numAttributes() - 1);
        Classifier clfFull = classifierBuilder.build(
                ClassifierType.RANDOM_FOREST, FeatureSelectionStrategy.NONE,
                BalancingStrategy.SMOTE, data, 1);
        double[] full = evaluator.evaluate(data, clfFull, RUNS, FOLDS, posIdx);
        log.info("Full: P={} R={} AUC={} Kappa={} NPofB20={}",
                fmt(full[0]), fmt(full[1]), fmt(full[2]), fmt(full[3]), fmt(full[4]));

        Instances dataNoSmells = removeAttribute(data, smellsColumn);
        log.info("Ablation (2/2): RF + SMOTE without {} ({} features) — estimated ~25 min",
                smellsColumn, dataNoSmells.numAttributes() - 1);
        Classifier clfReduced = classifierBuilder.build(
                ClassifierType.RANDOM_FOREST, FeatureSelectionStrategy.NONE,
                BalancingStrategy.SMOTE, dataNoSmells, 1);
        double[] reduced = evaluator.evaluate(dataNoSmells, clfReduced, RUNS, FOLDS, posIdx);
        log.info("Reduced: P={} R={} AUC={} Kappa={} NPofB20={}",
                fmt(reduced[0]), fmt(reduced[1]), fmt(reduced[2]), fmt(reduced[3]), fmt(reduced[4]));

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(buildRow("RF_SMOTE_all_features", data.numAttributes() - 1, full));
        rows.add(buildRow("RF_SMOTE_no_NSmells",   dataNoSmells.numAttributes() - 1, reduced));
        rows.add(buildDeltaRow(full, reduced));
        return rows;
    }

    private Instances removeAttribute(Instances data, String attributeName) throws Exception {
        int idx = data.attribute(attributeName).index();
        Remove remove = new Remove();
        remove.setAttributeIndices(String.valueOf(idx + 1));
        remove.setInputFormat(data);
        Instances result = Filter.useFilter(data, remove);
        result.setClassIndex(result.attribute("isBuggy").index());
        return result;
    }

    private Map<String, String> buildRow(String configuration, int numFeatures, double[] metrics) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Configuration", configuration);
        row.put("Num_Features",  String.valueOf(numFeatures));
        row.put("Precision",     fmt(metrics[0]));
        row.put("Recall",        fmt(metrics[1]));
        row.put("AUC",           fmt(metrics[2]));
        row.put("Kappa",         fmt(metrics[3]));
        row.put("NPofB20",       fmt(metrics[4]));
        return row;
    }

    private Map<String, String> buildDeltaRow(double[] full, double[] reduced) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Configuration", "Delta (no_NSmells - all_features)");
        row.put("Num_Features",  "-1");
        row.put("Precision",     fmt(reduced[0] - full[0]));
        row.put("Recall",        fmt(reduced[1] - full[1]));
        row.put("AUC",           fmt(reduced[2] - full[2]));
        row.put("Kappa",         fmt(reduced[3] - full[3]));
        row.put("NPofB20",       fmt(reduced[4] - full[4]));
        return row;
    }

    private String fmt(double v) {
        return String.format("%.4f", v);
    }
}
