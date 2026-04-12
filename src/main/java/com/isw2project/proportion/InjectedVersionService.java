package com.isw2project.proportion;

import com.isw2project.model.Issue;
import com.isw2project.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class InjectedVersionService {

    private static final Logger log = LoggerFactory.getLogger(InjectedVersionService.class);

    /**
     * For each issue without AV, predicts IV using:
     * IV = FV - (P * (FV - OV))
     * Then sets as AV all versions from IV (inclusive) to FV (exclusive).
     */
    public void predictAndAssignAV(Issue issue, List<Version> orderedVersions, double p, String projectKey) {
        List<Version> affectedVersions = issue.getFields().getAffectedVersions();
        boolean hasNoValidAV = affectedVersions == null || affectedVersions.isEmpty()
                || affectedVersions.stream().allMatch(v -> v.getReleaseDate() == null || v.getReleaseDate().isBlank());

        if (!hasNoValidAV) return;

        Version ov = issue.getFields().getOpeningVersion();
        Version fv = issue.getFields().getFixVersions() == null || issue.getFields().getFixVersions().isEmpty()
                ? null : issue.getFields().getFixVersions().getFirst();

        if (ov == null || fv == null) {
            log.warn("Project [{}] issue [{}] missing OV or FV, cannot predict IV.", projectKey, issue.getKey());
            return;
        }

        int ovIndex = indexOf(ov, orderedVersions);
        int fvIndex = indexOf(fv, orderedVersions);

        if (ovIndex < 0 || fvIndex < 0) {
            log.warn("Project [{}] issue [{}] OV or FV not found in ordered versions.", projectKey, issue.getKey());
            return;
        }
        if (fvIndex == ovIndex) {
            log.warn("Project [{}] issue [{}] OV equals FV, proportion cannot be applied.", projectKey, issue.getKey());
            return;
        }

        int ivIndex = Math.max(0, (int) Math.round(fvIndex - (p * (fvIndex - ovIndex))));

        // all versions from IV (inclusive) to FV (exclusive)
        List<Version> predictedAV = orderedVersions.subList(ivIndex, fvIndex);

        if (predictedAV.isEmpty()) {
            log.warn("Project [{}] issue [{}] predicted IV equals FV, no AV assigned.", projectKey, issue.getKey());
            return;
        }

        issue.getFields().setAffectedVersions(predictedAV);
    }

    private int indexOf(Version version, List<Version> orderedVersions) {
        for (int i = 0; i < orderedVersions.size(); i++) {
            if (orderedVersions.get(i).equals(version)) return i;
        }
        return -1;
    }
}