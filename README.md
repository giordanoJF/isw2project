# ISW2 Project — Bug Prediction on Apache OpenJPA

A study on software defect prediction using machine learning, structured in four milestones.

- **Milestone 1** downloads bug tickets from Jira, clones [Apache OpenJPA](https://github.com/apache/openjpa), extracts Java class snapshots for each release, computes 19 metrics, and labels each snapshot as buggy or clean.
- **Milestone 2** trains and evaluates three classifiers (RandomForest, NaiveBayes, IBk) over all combinations of feature selection and class balancing using 10×10-fold cross-validation (up to 60 combinations).
- **Milestone 3** trains the best classifier on the full dataset and measures how many buggy classes could have been prevented by having zero code smells. Includes a Spearman correlation analysis and an ablation study.
- **Milestone 4** runs PMD on the latest OpenJPA release (4.1.1), ranks classes by smell count, selects two candidates using a formula, and generates a detailed violation report. The refactored versions in `classi_refactored/` were produced separately via LLM using that report as input.

---

## Requirements

| Dependency | Version | M1 | M2 | M3 | M4 |
|---|---|---|---|---|---|
| Java | 26 | required | required | required | required |
| git CLI | any | required | — | — | required |
| Maven | 3.8+ | build only | build only | build only | build only |

> **Why git CLI for M1 and M4?** Both use `git ls-tree` and `git show` via `ProcessBuilder` to extract source files from specific commits. Repository cloning uses JGit (pure Java); only file extraction requires `git` on `PATH`. M2 and M3 only read the CSV produced by M1 — no git binary needed.

---

## Dependency order

- **M2 and M3 both depend on M1**: they read `output/milestone1/5_snapshots/OPENJPA_snapshots.csv`.
- **M4 depends on both M1 and the git clone**: it reads the snapshot CSV (to train the predictor and compute correlations) and extracts source directly from `gitclones/openjpa/`. Run M1 at least once before M4.
- **M2 and M3 are independent of each other** and can be run in any order after M1.

### Skipping earlier milestones with a saved snapshot

To run M2, M3, or M4 without re-running M1, copy the snapshot CSV from a save:

```bash
mkdir -p output/milestone1/5_snapshots
cp saves/<save-name>/milestone1/5_snapshots/OPENJPA_snapshots.csv \
   output/milestone1/5_snapshots/OPENJPA_snapshots.csv
```

M4 also caches extracted source in `output/milestone4/source/4.1.1/` — if that folder already exists the extraction step is skipped automatically.

---

## Build

```bash
mvn package
```

Produces four self-contained fat JARs in `target/`:

```
target/milestone1.jar
target/milestone2.jar
target/milestone3.jar
target/milestone4.jar
```

---

## Run

Run all commands from the **project root**.

```bash
java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar
java --enable-native-access=ALL-UNNAMED -jar target/milestone3.jar
java --enable-native-access=ALL-UNNAMED -jar target/milestone4.jar
```

> `--enable-native-access=ALL-UNNAMED` suppresses a JVM warning from Weka's native library loader (required on Java 24+). When running via `mvn exec:java` the flag is applied automatically via `.mvn/jvm.config`.

---

## Configuration

All behaviour is controlled by `src/main/resources/config.yaml` (baked into the JAR at build time). Edit and rebuild with `mvn package` to apply changes.

| Section | Used by | Purpose |
|---|---|---|
| `projects` | M1 | Jira project key |
| `git` | M1, M4 | Clone URL and local repo path |
| `csv` | M1 | Output directory |
| `metrics` | M1, M4 | PMD batch size, CPU fraction, snapshot percentage |
| `classifier` | M2 | Classifiers, feature selection, balancing, CV folds, parallelism |
| `whatif` | M3 | Best classifier settings, smell column name, output path |
| `refactoring` | M4 | Target release, LOC threshold, selection formula X, output paths |

### Enabling/disabling M2 combinations

Comment out entries in the `classifier` section to skip them:

```yaml
featureSelection:
  - NONE
  - INFO_GAIN
  - SPEARMAN
  # - FORWARD_SEARCH   # slow: ~1–3 h per combination
  # - BACKWARD_SEARCH  # slow: ~1–3 h per combination

balancing:
  - NONE
  - UNDERSAMPLING
  - OVERSAMPLING
  - SMOTE
```

Default configuration (wrappers disabled): 36 combinations. All enabled: 60 combinations.

---

## Output

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/        # raw Jira issues and versions
│   ├── 2_enriched_jira_data/   # after OV/AV enrichment
│   ├── 3_consistency_checked/  # after consistency checks
│   ├── 4_proportion_applied/   # after Proportion estimation
│   ├── 5_snapshots/            # OPENJPA_snapshots.csv  ← input for M2, M3, M4
│   └── 6_extracted_source/     # Java source per release (large, git-ignored)
├── milestone2/
│   └── OPENJPA_classifier.csv           # one row per (classifier × FS × balancing) combination
├── milestone3/
│   ├── OPENJPA_whatif_datasets.csv      # instance counts and predictions for A, B+, B, C
│   ├── OPENJPA_whatif_prevention.csv    # prevented bugs: total, proportion, of predictable
│   ├── OPENJPA_whatif_correlation.csv   # Spearman ρ between NSmells and each other feature
│   └── OPENJPA_whatif_ablation.csv      # CV metrics with and without NSmells (+ delta)
└── milestone4/
    ├── class_selection.csv              # all filtered classes ranked by Nsmells; 2 marked selected=true
    ├── smell_report.txt                 # full PMD violation detail for the 2 selected classes
    ├── feature_bugginess_correlation.csv # Spearman ρ between each feature and isBuggy (on dataset A)
    └── refactored_metrics.csv           # 19 metrics + isBuggy prediction for C0–CX of each class
```

`classi_refactored/` contains the LLM-produced versions of the two selected classes (C1–C4 for BrokerImpl, C1–C2 for LoginDialog). These are tracked in git as they are not regenerable deterministically.

`output/milestone1/6_extracted_source/` and `output/milestone4/source/` are git-ignored (large source trees, recreated automatically on re-run).

---

## Saving output snapshots

A helper script saves the current `output/` folder (excluding large source trees) to a named snapshot:

```bash
bash save.sh <name>
# example:
bash save.sh After_M4
```

Snapshots are stored in `saves/<name>/` and are tracked in git.
