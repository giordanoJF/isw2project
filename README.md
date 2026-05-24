# ISW2 Project — Bug Prediction on Apache OpenJPA

A two-milestone study on software defect prediction using machine learning.

- **Milestone 1** downloads bug tickets from Jira, extracts Java class snapshots from the Git history of [Apache OpenJPA](https://github.com/apache/openjpa), computes process and product metrics, and labels each snapshot as buggy or clean.
- **Milestone 2** trains and evaluates classifiers (RandomForest, NaiveBayes, IBk) over all combinations of feature selection and class-balancing strategies using 10-times 10-fold cross-validation.

---

## Requirements

| Dependency | Version | Milestone 1 | Milestone 2 |
|---|---|---|---|
| Java | 26 | required | required |
| git | any | required | not required |
| Maven | 3.8+ | build only | build only |

> **Why git for Milestone 1?** Snapshot extraction calls `git ls-tree` and `git show` via `ProcessBuilder`. The repository clone itself is handled by JGit (pure Java, no git binary needed for that step), but extraction still requires a system `git` on the `PATH`.
>
> **Milestone 2 is fully independent.** It reads the snapshot CSV produced by Milestone 1 and needs no git at all.

---

## Build

```bash
mvn package
```

Produces two self-contained fat JARs in `target/`:

```
target/milestone1.jar
target/milestone2.jar
```

---

## Run

```bash
# Milestone 1 — downloads Jira data, clones repo, extracts snapshots, computes metrics
java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar

# Milestone 2 — trains and evaluates classifiers, writes results CSV
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar
```

Run both commands from the project root so that output folders are created there.

> `--enable-native-access=ALL-UNNAMED` suppresses a JVM warning from Weka's native library loader (required on Java 24+). The code works without it, but warnings are printed to stderr.

When running via Maven instead of a fat JAR, the flag is applied automatically via `.mvn/jvm.config` and does not need to be passed manually.

---

## Configuration

All behaviour is controlled by `src/main/resources/config.yaml` (baked into the JAR at build time):

| Section | Used by | Purpose |
|---|---|---|
| `projects` | M1 | Jira project key and JQL filter |
| `git` | M1 | Clone URL and local repo path |
| `csv` | M1 | Output directory and column selection |
| `metrics` | M1 | PMD batch size, CPU fraction, snapshot percentage |
| `classifier` | M2 | Classifiers, feature selection, balancing, CV folds, parallelism |

To change settings, edit `config.yaml` and rebuild with `mvn package`.

### Enabling/disabling combinations (Milestone 2)

Comment out any entry in the `classifier` section to exclude it from the run:

```yaml
featureSelection:
  - NONE
  - INFO_GAIN
  - SPEARMAN
  # - FORWARD_SEARCH   # disabled — ~1-3 h per combination
  # - BACKWARD_SEARCH  # disabled — ~1-3 h per combination

balancing:
  - NONE
  - UNDERSAMPLING
  - OVERSAMPLING
  # - SMOTE            # disabled by default
```

Default: 27 combinations (~35 min with 4 threads). All enabled: 60 combinations (hours to days).

---

## Output

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/        # raw Jira issues and versions
│   ├── 2_enriched_jira_data/   # after OV/AV enrichment
│   ├── 3_consistency_checked/  # after consistency checks
│   ├── 4_proportion_applied/   # after Proportion estimation
│   ├── 5_snapshots/            # OPENJPA_snapshots.csv  ← input for M2
│   └── 6_extracted_source/     # Java source files extracted from Git
└── milestone2/
    └── OPENJPA_classifier.csv  # one row per (classifier × FS × balancing) combination
```

---

## Project structure

```
src/main/java/com/isw2project/
├── Milestone1Main.java
├── Milestone2Main.java
├── buggyness/       — bugginess labeling
├── classifier/      — ML pipeline (Weka wrappers, custom SMOTE and Spearman)
├── config/          — AppConfig, ConfigLoader, MetricsConfig
├── consistency/     — consistency checks on Jira data
├── csv/             — CSV export
├── downloader/      — Jira REST API client
├── enricher/        — OV/AV/FV enrichment
├── gitextractor/    — snapshot extraction from Git tags
├── metrics/         — LOC, churn, EXP, SEXP, PMD code smells
├── model/           — domain entities (Issue, Version, JavaClassSnapshot, ...)
├── proportion/      — Proportion estimation for missing AV
└── repocloner/      — automatic Git clone via JGit
```
