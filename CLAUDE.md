# CLAUDE.md — ISW2 Project

## Regole sui commit

**Non includere mai** Co-Authored-By, "Generated with Claude", né alcuna attribuzione a Claude nei messaggi di commit. L'utente vuole commit puliti senza traccia di AI assistance.

---

## Panoramica del progetto

Studio di bug prediction su **Apache OpenJPA** in due milestone:

- **Milestone 1**: scarica ticket Jira, clona il repo, estrae snapshot di classi Java per release, calcola 19 metriche (process + product), etichetta ogni snapshot come buggy/clean.
- **Milestone 2**: addestra e valuta classificatori (RandomForest, NaiveBayes, IBk) su tutte le combinazioni di feature selection e class balancing con 10-times 10-fold CV.

---

## Build & run

```bash
mvn package                                              # produce target/milestone1.jar e target/milestone2.jar

java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar
```

`--enable-native-access=ALL-UNNAMED` è richiesto da Java 24+ per il native loader di Weka. Con Maven exec plugin è già in `.mvn/jvm.config`, con `java -jar` va passato manuale.

---

## Dipendenze di sistema

| Dipendenza | Versione | M1 | M2 |
|---|---|---|---|
| Java | 26 | richiesta | richiesta |
| git CLI | qualsiasi | richiesta | non richiesta |
| Maven | 3.8+ | solo build | solo build |

**Perché git per M1?** `GitFileExtractorService` e `CommitIndexService` usano `ProcessBuilder("git", ...)` per `git ls-tree`, `git show`, e comandi di log. Il clone è fatto con JGit (puro Java), ma l'estrazione richiede git sul PATH.

---

## Struttura output

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/          # issues+versions grezzi da Jira
│   ├── 2_enriched_jira_data/     # dopo enrichment OV/AV
│   ├── 3_consistency_checked/    # dopo consistency check
│   ├── 4_proportion_applied/     # dopo Proportion
│   ├── 5_snapshots/              # OPENJPA_snapshots.csv  ← input M2
│   └── 6_extracted_source/       # sorgenti Java da Git per release
└── milestone2/
    └── OPENJPA_classifier.csv    # una riga per (clf × FS × balancing)
```

---

## Configurazione (`src/main/resources/config.yaml`)

Sezione `classifier` (M2):
- `classifiers`: RANDOM_FOREST, NAIVE_BAYES, IBK
- `featureSelection`: NONE, INFO_GAIN, SPEARMAN (FORWARD_SEARCH/BACKWARD_SEARCH commentati: 1-3h per combinazione)
- `balancing`: NONE, UNDERSAMPLING, OVERSAMPLING (SMOTE commentato, più lento)
- `crossValidation.runs/folds`: 10 × 10
- `parallelism.interactive`: true → prompt runtime, false → legge values dal file
- `parallelism.combinations` / `parallelism.randomForestSlots`: "auto" | "N" | "N%"
  - I due valori si moltiplicano: con entrambi "25%" su 8 core = 2 thread totali
  - Portatile: `combinations: "50%"`, `randomForestSlots: "1"`
  - PC fisso: entrambi `"auto"`

Default: 27 combinazioni (3 clf × 3 FS × 3 bal, ~35 min con 4 thread). Tutto abilitato: 60 combinazioni (ore/giorni).

---

## Architettura e convenzioni di codice

### Pattern obbligatorio per ogni package

- `*Orchestrator`: coordina, non contiene logica, **nessun try-catch, nessun `throws` nella firma**.
- `*Service`: contiene la logica, può lanciare eccezioni checked.
- `Main.java`: nessun try-catch, nessun `throws`.

### Regole di stile

- Commenti in inglese, niente emoji.
- `LinkedHashMap` dove l'ordine delle colonne conta (righe CSV, mappe metriche).
- Niente feature flag, niente shim di retrocompatibilità.
- Logging: SLF4J + Logback. Pattern: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger -- %msg%n`.
- `GitCommandException` è l'unica eccezione checked di dominio.
- Niente `break`/`continue` oltre uno per loop.
- PMD è attivo sul codice: le violazioni vengono riportate come code smell nel dataset.

### Soppressione warning

- JVM: `--enable-native-access=ALL-UNNAMED` (Weka jniloader, Java 24+)
- `@SuppressWarnings({"unchecked","rawtypes","deprecation"})` su `SmoteFilter` (codice legacy Weka)
- `@SuppressWarnings("java:S1172")` su main args (SonarQube)
- `@SuppressWarnings("java:S106")` su `System.out` nel configuratore interattivo (SonarQube)

---

## Componenti custom (non in weka-stable 3.8.6)

- `SpearmanAttributeEval`: `ASEvaluation` + `AttributeEvaluator` per Spearman rank correlation
- `SmoteFilter`: `SimpleBatchFilter` SMOTE custom (SMOTE non è in Maven Central per weka-stable 3.8.6)

---

## Pipeline M2 — dettagli tecnici rilevanti

- **`FilteredClassifier` + `MultiFilter`**: applica balancing+FS solo sul training fold → nessun data leakage.
- **Ordine filtri**: balancing prima, poi FS (FS vede distribuzione già bilanciata).
- **Predizioni accumulate**: ogni istanza viene predetta da un modello che non l'ha mai vista in training; l'accumulo su 100 run×fold dà stabilità per NPofB20.
- **NaN handling**: `safe(double v)` → 0.0 per NaN/Infinite prima dell'accumulo metriche.
- **19 metriche** (non 17): LOC, Previous_Release_Code_Smells, LOC_Touched, NR, Nfix, Nauth, LOC_Added, Max_LOC_Added, Avg_LOC_Added, Churn, Max_Churn, Avg_Churn, Change_Set_Size, Max_Change_Set, Avg_Change_Set, Age, Weighted_Age, EXP, SEXP.
- **Perché Precision+Recall e non F1**: F1 nasconde se il problema è bassa precision o basso recall; presuppone pesi uguali, non sempre vero in bug prediction.
- **Perché Resample e non duplicazione diretta**: Resample è l'unico filtro di oversampling nel core jar di weka-stable 3.8.6; duplicazione custom richiederebbe un filtro apposito.

---

## Report LaTeX (`latexreport/`)

Il report è in `latexreport/` (compilato su Overleaf, non committato nel repo). La struttura segue il template del corso.

### Stato sezioni

| File | Stato |
|---|---|
| `metodologia_m1.tex` | Completo. Aggiunta subsubsection "Aggiornamenti alla pipeline durante la M2" (JGit/git, MetricsConfig, riorganizzazione cartelle). |
| `risultati_m1.tex` | Completo (originale). |
| `metodologia_m2.tex` | Completo. Include: CV correttezza predizioni accumulate, perché P+R non F1, perché Resample, gestione NaN, soppressione warning. |
| `risultati_m2.tex` | Run preliminare inserito (27 combinazioni). **Manca: risultati run finale** (con SMOTE/wrapper/dataset aggiuntivi). |
| `minacce.tex` | Completo per M1 e M2. |
| `conclusioni.tex` | M1 completa (fix: 17→19 metriche). M2 con run preliminare. **Manca: aggiornamento con run finale.** |
| `takeaway.tex` | Completo per M1 e M2. |

### Lavoro pendente

**Unica cosa mancante**: risultati del run completo (con SMOTE abilitato, eventualmente wrapper FS, eventualmente dataset aggiuntivi BOOKKEEPER/KAFKA) → aggiornare `risultati_m2.tex` e `conclusioni.tex`.

---

## Walk-Forward Validation (non implementata)

La domanda del professore "cambia in base al numero di release?" richiederebbe Walk-Forward. Non implementata: servirebbe `WalkForwardSplitter`, `WalkForwardEvaluatorService` (train/test diretto, no CV), `WalkForwardOrchestrator`, secondo CSV di output. Documentato nel report (sezione risultati M2, domande aperte).

---

## Package structure

```
src/main/java/com/isw2project/
├── Milestone1Main.java
├── Milestone2Main.java
├── buggyness/       — bugginess labeling
├── classifier/      — pipeline ML (Weka wrappers, SmoteFilter, SpearmanAttributeEval)
├── config/          — AppConfig, ConfigLoader, MetricsConfig
├── consistency/     — consistency check su dati Jira
├── csv/             — export CSV
├── downloader/      — client REST Jira
├── enricher/        — enrichment OV/AV/FV
├── gitextractor/    — estrazione snapshot da tag Git (usa git CLI)
├── metrics/         — LOC, churn, EXP, SEXP, PMD code smell
├── model/           — entità di dominio (Issue, Version, JavaClassSnapshot, ...)
├── proportion/      — stima Proportion per AV mancanti
└── repocloner/      — clone automatico via JGit (no git CLI)
```