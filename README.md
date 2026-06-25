# ISW2 Project — Bug Prediction on Apache OpenJPA

A study on software defect prediction using machine learning, structured in four milestones.

- **Milestone 1** downloads bug tickets from Jira, clones the [Apache OpenJPA](https://github.com/apache/openjpa) repository, extracts Java class snapshots for each release, computes 19 process and product metrics, and labels each snapshot as buggy or clean.
- **Milestone 2** trains and evaluates three classifiers (RandomForest, NaiveBayes, IBk) over all combinations of feature selection strategies and class-balancing strategies using 10-times 10-fold cross-validation.
- **Milestone 3** performs a what-if counterfactual analysis: it trains the best classifier on the full dataset and measures how many buggy classes could have been prevented by having zero code smells. Includes a Spearman correlation analysis and an ablation study.
- **Milestone 4** selects two Java classes from the latest OpenJPA release using a PMD smell ranking and a name-based formula, then produces incrementally tested refactored versions ($C_1$–$C_4$) via LLM.

---

## Requirements

| Dependency | Version | M1 | M2 | M3 | M4 |
|---|---|---|---|---|---|
| Java | 26 | required | required | required | required |
| git CLI | any | required | — | — | required |
| Maven | 3.8+ | build only | build only | build only | build only |

> **Why git CLI for M1 and M4?**
> M1 uses `git ls-tree` and `git show` via `ProcessBuilder` to extract source files from specific commits. M4 does the same for the latest release.
> Repository cloning itself uses JGit (pure Java); only file extraction requires a system `git` on `PATH`.
>
> **M2 and M3 need no git binary.** They only read the CSV produced by M1.

---

## Dependency order

```
M1  ──────────────────────►  M2
 │                            (reads output/milestone1/5_snapshots/OPENJPA_snapshots.csv)
 └──────────────────────────► M3
                              (reads output/milestone1/5_snapshots/OPENJPA_snapshots.csv)

gitclones/openjpa (git clone)
 └──────────────────────────► M4
                              (reads directly from the local git clone — independent of M1 output)
```

- **M2 depends on M1**: needs `output/milestone1/5_snapshots/OPENJPA_snapshots.csv`.
- **M3 depends on M1**: needs the same snapshot CSV.
- **M4 is independent of M1 output**: extracts `4.1.1` directly from `gitclones/openjpa/`.  
  If the clone does not exist yet, run M1 at least once (it clones automatically). After that M4 can be re-run alone without re-running M1.
- **M2 and M3 are independent of each other** and can be run in any order after M1.

### Running a milestone without re-running its dependencies

If you already have a saved output (see [Saving output snapshots](#saving-output-snapshots)) and want to skip earlier milestones:

**To run M2 or M3 without re-running M1**, copy the snapshot CSV from your save:
```bash
mkdir -p output/milestone1/5_snapshots
cp saves/<save-name>/milestone1/5_snapshots/OPENJPA_snapshots.csv \
   output/milestone1/5_snapshots/OPENJPA_snapshots.csv
```
Then run M2 or M3 normally — they only need that one file.

**To run M4 without re-running M1**, the git clone must exist:
```bash
# If gitclones/openjpa/ already exists, M4 runs immediately.
# If it does not, run M1 once to let it clone, then you can skip M1 on future runs.
```
M4 caches the extracted source in `output/milestone4/source/4.1.1/` — if that folder already
exists the extraction step is skipped automatically.

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

Run all commands from the **project root** so that `output/` and `gitclones/` are created in the right place.

```bash
# Milestone 1 — downloads Jira data, clones repo, extracts snapshots, computes metrics
java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar

# Milestone 2 — trains and evaluates classifiers, writes results CSV
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar

# Milestone 3 — what-if analysis + Spearman correlation + ablation study
java --enable-native-access=ALL-UNNAMED -jar target/milestone3.jar

# Milestone 4 — PMD smell ranking on 4.1.1, class selection, LLM refactoring pipeline
java --enable-native-access=ALL-UNNAMED -jar target/milestone4.jar
```

> `--enable-native-access=ALL-UNNAMED` suppresses a JVM warning from Weka's native library loader (required on Java 24+). The code works without it but warnings appear on stderr. When running via `mvn exec:java` the flag is applied automatically via `.mvn/jvm.config`.

### Expected runtimes

| Milestone | Typical duration |
|---|---|
| M1 (full run) | ~5–6 minutes |
| M2 (27 combinations, 4 threads) | ~35 minutes |
| M2 (60 combinations with SMOTE + wrapper FS, 4 threads) | ~1–2 hours |
| M3 (what-if + correlation) | ~10 seconds |
| M3 (+ ablation study, single thread) | ~50 minutes additional |
| M4 (source extraction + PMD on 4.1.1) | ~2–3 minutes |

---

## Configuration

All behaviour is controlled by `src/main/resources/config.yaml` (baked into the JAR at build time). To change any setting, edit the file and rebuild with `mvn package`.

| Section | Used by | Purpose |
|---|---|---|
| `projects` | M1 | Jira project key |
| `git` | M1, M4 | Clone URL and local repo path |
| `csv` | M1 | Output directory |
| `metrics` | M1, M4 | PMD batch size, CPU fraction, snapshot percentage |
| `classifier` | M2 | Classifiers, feature selection, balancing, CV folds, parallelism |
| `whatif` | M3 | Best classifier settings, smell column name, output path |
| `refactoring` | M4 | Target release, LOC threshold, selection X, output paths |

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
  combinations: "auto"      # concurrent classifier combinations (auto | N | N%)
  randomForestSlots: "auto" # RF internal threads (auto | N)
```

The product of `combinations` and `randomForestSlots` should not exceed the number of physical cores.
On a laptop, use `combinations: "50%"` and `randomForestSlots: "1"` to avoid thermal throttling.

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
│   └── 6_extracted_source/     # Java source per release (large, git-ignored)
├── milestone2/
│   └── OPENJPA_classifier.csv  # one row per (classifier × FS × balancing) combination
├── milestone3/
│   ├── OPENJPA_whatif_datasets.csv    # instance counts and predictions for A, B+, B, C
│   ├── OPENJPA_whatif_prevention.csv  # prevented bugs: total, proportion, of predictable
│   ├── OPENJPA_whatif_correlation.csv # Spearman rho between NSmells and each other feature
│   └── OPENJPA_whatif_ablation.csv    # CV metrics with and without NSmells (+ delta)
└── milestone4/
    ├── class_selection.csv     # all filtered classes ranked by Nsmells, 2 marked selected=true
    └── source/
        └── 4.1.1/              # Java source extracted from git tag 4.1.1 (git-ignored)
```

`output/milestone1/6_extracted_source/` and `output/milestone4/source/` are excluded from git
tracking because they contain full Java source trees (~hundreds of MB). Both are recreated
automatically on the next run of M1 or M4 respectively.

---

## Saving output snapshots

A helper script is included to save the current `output/` folder (excluding large source trees) to a named snapshot:

```bash
bash save.sh <name>
# example:
bash save.sh After_M4
```

Snapshots are stored in `saves/<name>/` and are tracked in git.
