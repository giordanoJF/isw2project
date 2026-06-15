# ISW2 Project — Bug Prediction on Apache OpenJPA

A study on software defect prediction using machine learning, structured in three milestones.

- **Milestone 1** downloads bug tickets from Jira, clones the [Apache OpenJPA](https://github.com/apache/openjpa) repository, extracts Java class snapshots for each release, computes 19 process and product metrics, and labels each snapshot as buggy or clean.
- **Milestone 2** trains and evaluates three classifiers (RandomForest, NaiveBayes, IBk) over all combinations of feature selection strategies and class-balancing strategies using 10-times 10-fold cross-validation.
- **Milestone 3** performs a what-if counterfactual analysis: it trains the best classifier on the full dataset and measures how many buggy classes could have been prevented by having zero code smells. It also includes a Spearman correlation analysis and an ablation study to assess the redundancy of the code smell feature.

---

## Requirements

| Dependency | Version | M1 | M2 | M3 |
|---|---|---|---|---|
| Java | 26 | required | required | required |
| git CLI | any | required | not required | not required |
| Maven | 3.8+ | build only | build only | build only |

> **Why git CLI for Milestone 1?** Snapshot extraction calls `git ls-tree` and `git show` via `ProcessBuilder`. The repository clone itself is handled by JGit (pure Java, no git binary needed for cloning), but file extraction still requires a system `git` on the `PATH`.
>
> **Milestones 2 and 3 are fully independent of git.** They read the CSV produced by Milestone 1 and need no git binary at all.

---

## What you need in the project folder

When running from scratch on a new device the following must be present before starting:

- **`src/main/resources/config.yaml`** — baked into the JAR at build time. Contains all runtime configuration (Jira URL, clone URL, output paths, classifier settings). Already present in the repository.
- **`gitclones/`** — created automatically by Milestone 1 when it clones the OpenJPA repository. Do not create it manually; do not delete it between runs (the clone step is skipped if `.git/` already exists inside).
- **`output/`** — created automatically by each milestone on first run.

Everything else (dependencies, Weka, PMD) is downloaded by Maven at build time.

---

## Build

```bash
mvn package
```

Produces three self-contained fat JARs in `target/`:

```
target/milestone1.jar
target/milestone2.jar
target/milestone3.jar
```

---

## Run

Run all commands from the **project root** so that `output/` and `gitclones/` are created in the right place.

```bash
# Milestone 1 — downloads Jira data, clones repo, extracts snapshots, computes metrics
java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar

# Milestone 2 — trains and evaluates classifiers, writes results CSV
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar

# Milestone 3 — what-if analysis + Spearman correlation + ablation study
java --enable-native-access=ALL-UNNAMED -jar target/milestone3.jar
```

Each milestone depends on the previous one's output:
- M2 reads `output/milestone1/5_snapshots/OPENJPA_snapshots.csv`
- M3 reads the same snapshot CSV

> `--enable-native-access=ALL-UNNAMED` suppresses a JVM warning from Weka's native library loader (required on Java 24+). The code works without it but warnings are printed to stderr. When running via `mvn exec:java` the flag is applied automatically via `.mvn/jvm.config`.

### Expected runtimes

| Milestone | Typical duration |
|---|---|
| M1 (full run) | ~5–6 minutes |
| M2 (27 combinations, 4 threads) | ~35 minutes |
| M2 (60 combinations with SMOTE + wrapper FS, 4 threads) | ~1–2 hours |
| M3 (what-if + correlation) | ~10 seconds |
| M3 (+ ablation study, single thread) | ~50 minutes additional |

---

## Configuration

All behaviour is controlled by `src/main/resources/config.yaml` (baked into the JAR at build time). To change any setting, edit the file and rebuild with `mvn package`.

| Section | Used by | Purpose |
|---|---|---|
| `projects` | M1 | Jira project key |
| `git` | M1 | Clone URL and local repo path |
| `csv` | M1 | Output directory |
| `metrics` | M1 | PMD batch size, CPU fraction, snapshot percentage |
| `classifier` | M2 | Classifiers, feature selection, balancing, CV folds, parallelism |
| `whatif` | M3 | Best classifier settings, smell column name, output path |

### Enabling/disabling M2 combinations

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
  - SMOTE
```

Default: 27 combinations (~35 min with 4 threads). All enabled: 60 combinations.

### Parallelism (Milestone 2)

```yaml
parallelism:
  interactive: false        # true = prompt at runtime, false = read from config
  combinations: "50%"       # concurrent classifier combinations (auto | N | N%)
  randomForestSlots: "1"    # RF internal threads (auto | N)
```

The product of `combinations` and `randomForestSlots` should not exceed the number of physical cores.

---

## Output

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/        # raw Jira issues and versions
│   ├── 2_enriched_jira_data/   # after OV/AV enrichment
│   ├── 3_consistency_checked/  # after consistency checks
│   ├── 4_proportion_applied/   # after Proportion estimation
│   ├── 5_snapshots/            # OPENJPA_snapshots.csv  ← input for M2 and M3
│   └── 6_extracted_source/     # Java source files extracted from Git (large, not tracked)
├── milestone2/
│   └── OPENJPA_classifier.csv  # one row per (classifier × FS × balancing) combination
└── milestone3/
    ├── OPENJPA_whatif_datasets.csv    # instance counts and predictions for A, B+, B, C
    ├── OPENJPA_whatif_prevention.csv  # prevented bugs: total, proportion, of predictable
    ├── OPENJPA_whatif_correlation.csv # Spearman rho between NSmells and each other feature
    └── OPENJPA_whatif_ablation.csv    # CV metrics with and without NSmells (+ delta row)
```

The `output/milestone1/6_extracted_source/` folder is excluded from git tracking because it contains the full Java source of OpenJPA (~hundreds of MB). It is recreated by Milestone 1.

---

## Saving output snapshots

A helper script is included to save the current `output/` folder (excluding the large `6_extracted_source/`) to a named snapshot:

```bash
bash save.sh <name>
# example:
bash save.sh After_M3.1
```

Snapshots are stored in `saves/<name>/` and are tracked in git.

---

## Project structure

```
src/main/java/com/isw2project/
├── Milestone1Main.java
├── Milestone2Main.java
├── Milestone3Main.java
├── buggyness/       — bugginess labeling
├── classifier/      — ML pipeline (Weka wrappers, custom SMOTE and Spearman)
├── config/          — AppConfig, ConfigLoader, WhatIfConfig, MetricsConfig
├── consistency/     — consistency checks on Jira data
├── csv/             — CSV export
├── downloader/      — Jira REST API client
├── enricher/        — OV/AV/FV enrichment
├── gitextractor/    — snapshot extraction from Git tags
├── metrics/         — LOC, churn, EXP, SEXP, PMD code smells
├── model/           — domain entities (Issue, Version, JavaClassSnapshot, ...)
├── proportion/      — Proportion estimation for missing AV
├── repocloner/      — automatic Git clone via JGit
└── whatif/          — what-if analysis, Spearman correlation, ablation study
```
