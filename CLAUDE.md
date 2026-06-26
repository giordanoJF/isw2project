# CLAUDE.md — ISW2 Project

## Regole sui commit

**NON COMMITTARE MAI DI PROPRIA INIZIATIVA.** Eseguire `git commit` solo quando l'utente lo chiede esplicitamente.

**Commit modulari**: ogni commit deve riguardare una sola feature o tipo di modifica. Non raggruppare in un unico commit modifiche al codice, modifiche al report e modifiche alla configurazione — sono commit separati. Esempio: un commit per la nuova feature Java, un commit separato per il report, un altro per il CLAUDE.md.

**Non includere mai** Co-Authored-By, "Generated with Claude", né alcuna attribuzione a Claude nei messaggi di commit.

---

## Panoramica del progetto

Studio di bug prediction su **Apache OpenJPA** in quattro milestone:

- **Milestone 1**: scarica ticket Jira, clona il repo, estrae snapshot di classi Java per release, calcola 19 metriche (process + product), etichetta ogni snapshot come buggy/clean.
- **Milestone 2**: addestra e valuta classificatori (RandomForest, NaiveBayes, IBk) su tutte le combinazioni di feature selection e class balancing con 10-times 10-fold CV.
- **Milestone 3**: analisi controfattuale — quante classi buggy sarebbero state prevenute in assenza di code smell; studio di ridondanza (correlazione Spearman e ablation study).
- **Milestone 4**: selezione automatica di classi da refactorare in base a formula docente; report violazioni PMD con scheletro prompt Copilot.

**Stack tecnologico:** Java 26, Maven, Jackson, PMD 7, SLF4J + Logback, JGit 7.1.0 (clone), Git via ProcessBuilder (estrazione), Weka 3.8.6, jul-to-slf4j.

**Package root:** `com.isw2project`

---

## Build & run

```bash
mvn package

java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar
```

`--enable-native-access=ALL-UNNAMED` è richiesto da Java 24+ per il native loader di Weka. Con Maven exec plugin è già in `.mvn/jvm.config`; con `java -jar` va passato manuale.

Da IntelliJ va inserito nelle **VM options** della run configuration.

---

## Dipendenze di sistema

| Dipendenza | Versione | M1 | M2 |
|---|---|---|---|
| Java | 26 | richiesta | richiesta |
| git CLI | qualsiasi | richiesta | non richiesta |
| Maven | 3.8+ | solo build | solo build |

**Perché git per M1?** `GitFileExtractorService` e `CommitIndexService` usano `ProcessBuilder("git", ...)` per `git ls-tree`, `git show`, `git log`. Il clone è fatto con JGit (puro Java), ma l'estrazione richiede git sul PATH.

---

## Struttura output

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/          # issues+versions grezzi da Jira
│   ├── 2_enriched_jira_data/     # dopo enrichment OV/AV
│   ├── 3_consistency_checked/    # dopo consistency check
│   ├── 4_proportion_applied/     # dopo Proportion
│   ├── 5_snapshots/              # OPENJPA_snapshots.csv  ← input M2/M3
│   └── 6_extracted_source/       # sorgenti Java da Git per release
├── milestone2/
│   └── OPENJPA_classifier.csv    # una riga per (clf × FS × balancing)
├── milestone3/
│   ├── OPENJPA_whatif_prevention.csv   # classi buggy prevenibili senza smells
│   ├── OPENJPA_whatif_datasets.csv     # dataset con/senza smells
│   ├── OPENJPA_whatif_correlation.csv  # correlazione Spearman per feature
│   └── OPENJPA_whatif_ablation.csv     # ablation study per smell threshold
└── milestone4/
    ├── class_selection.csv       # classi ranked con flag selected
    └── smell_report.txt          # violazioni PMD per le classi selezionate
```

---

## Configurazione (`src/main/resources/config.yaml`)

```yaml
baseUrl: https://issues.apache.org/jira
projects:
  - key: OPENJPA
git:
  cloneUrl: https://github.com/apache/openjpa.git
  repoDir: gitclones/openjpa
  codeOutputDir: output/milestone1/6_extracted_source
csv:
  outputDir: output/milestone1
metrics:
  snapshotPercentage: 1.0    # 1.0 = tutti; 0.1 = 10% per debug rapido
  pmd:
    batchSize: 100            # file per invocazione PMD
    cpuFraction: 0.5          # frazione core per thread PMD
classifier:
  inputCsv: output/milestone1/5_snapshots/OPENJPA_snapshots.csv
  outputDir: output/milestone2
  datasetName: OPENJPA
```

Sezione `classifier` (M2):
- `classifiers`: RANDOM_FOREST, NAIVE_BAYES, IBK
- `featureSelection`: NONE, INFO_GAIN, SPEARMAN, FORWARD_SEARCH, BACKWARD_SEARCH (i wrapper sono lenti: 1-3h per combinazione)
- `balancing`: NONE, UNDERSAMPLING, OVERSAMPLING, SMOTE
- `crossValidation.runs/folds`: 10 × 10
- `parallelism.interactive`: true → prompt runtime, false → legge dal file
- `parallelism.combinations` / `randomForestSlots`: "auto" | "N" | "N%" — i due si moltiplicano
  - Portatile: `combinations: "50%"`, `randomForestSlots: "1"`
  - PC fisso: entrambi `"auto"`

---

## Architettura e convenzioni di codice

### Pattern obbligatorio per ogni package

- `*Orchestrator`: coordina senza contenere logica. Chiama solo i service. **Nessun try-catch, nessun `throws` nella firma.**
- `*Service`: contiene la logica implementativa, può lanciare eccezioni checked.
- `Main.java`: nessun try-catch, nessun `throws`.

### Regole di stile

- Commenti in inglese, niente emoji.
- `LinkedHashMap` dove l'ordine delle colonne conta (righe CSV, mappe metriche).
- Niente feature flag, niente shim di retrocompatibilità.
- Logging: SLF4J + Logback. Pattern: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger -- %msg%n`. PMD silenziato: `<logger name="net.sourceforge.pmd" level="OFF"/>`.
- `GitCommandException` è l'unica eccezione checked di dominio.
- Niente `break`/`continue` oltre uno per loop.
- PMD è attivo sul codice: le violazioni vengono riportate come code smell nel dataset.

### Soppressione warning

- JVM: `--enable-native-access=ALL-UNNAMED` (Weka jniloader, Java 24+)
- `@SuppressWarnings({"unchecked","rawtypes","deprecation"})` su `SmoteFilter` (codice legacy Weka)
- `@SuppressWarnings("java:S1172")` su main args (SonarQube)
- `@SuppressWarnings("java:S106")` su `System.out` nel configuratore interattivo (SonarQube)

---

## Package structure e dipendenze

```
com.isw2project/
├── config/           AppConfig, ClassifierConfig, CrossValidationConfig, ParallelismConfig,
│                     MetricsConfig, CsvConfig, GitConfig, ProjectConfig, RefactoringConfig,
│                     WhatIfConfig, ConfigLoader, InteractiveParallelismConfigurator
├── model/            Issue, Version, ProjectData, JavaClassSnapshot, ReleaseSnapshot
├── downloader/       client REST Jira
├── enricher/         enrichment OV/AV/FV
├── consistency/      check di consistenza su ticket e versioni
│   └── checks/       una classe per ogni check
├── proportion/       stima AV mancanti con Proportion
├── csv/              serializzazione dati su CSV
├── gitextractor/     estrazione snapshot classi Java da Git (usa git CLI)
│   └── support/      CommitIndexService, GitLogStatsService
├── buggyness/        labeling isBuggy sugli snapshot
├── metrics/          calcolo metriche per ogni snapshot
│   └── impl/         una classe per ogni metrica (implementano Metric)
├── repocloner/       clone automatico via JGit (no git CLI)
├── classifier/       Milestone 2: pipeline ML Weka
├── whatif/           Milestone 3: analisi controfattuale code smell
└── refactoring/      Milestone 4: selezione classi e report smell
    ├── ReleaseSourceService        estrae sorgenti release da Git (skip se già presenti)
    ├── NsmellsService              PMD su tutti i file → Map<path, Nsmells> ordinata
    ├── ClassSizeFilterService      filtri: LOC<threshold, interfacce/abstract, Nsmells<minThreshold
    ├── ClassSelectorService        applica formula X → List<String> (2 classi)
    ├── SmellDetailService          PMD sulle 2 classi selezionate → violazioni complete
    ├── ViolationDetail             record: ruleName, ruleSetName, beginLine, endLine, description
    └── ClassSelectionOrchestrator  coordina; scrive class_selection.csv e smell_report.txt
```

**Dipendenze tra package:** `gitextractor.support` è l'unico package trasversale, usato da `buggyness` e `metrics`. Nessun'altra dipendenza laterale tra package allo stesso livello.

---

## Modelli dati chiave

### `Version.java`
```java
String id, name, description;
boolean released;
String releaseDate;  // formato "yyyy-MM-dd"
```

### `Issue.java`
Modello Jira con inner classes `Fields`, `Status`, `IssueType`, `Priority`. Campi rilevanti:
- `String created` — data creazione
- `List<Version> affectedVersions` — AV
- `List<Version> fixVersions` — FV
- `Version openingVersion` — calcolata dall'enricher
- `getInjectedVersion()` — restituisce la AV meno recente

### `JavaClassSnapshot.java`
```java
String classPath;           // path relativo nel repo
String release;             // nome versione Jira
Path codeFilePath;          // path su disco del sorgente estratto
boolean buggy;              // settato da BugginessLabelerService
Map<String, String> metrics; // settato da MetricsOrchestrator
```

### `ReleaseSnapshot.java`
```java
String release;
List<JavaClassSnapshot> classes;
```

---

## Flusso Milestone1Main.java

```java
// 0. Clone automatico repo (skip se .git/ esiste già)
new RepoCloneOrchestrator(new RepoCloneService()).ensureCloned(config.getGit());

// 1-4. Download → enriching → consistency check → proportion
// (export CSV a ogni passo: 1_raw_jira_data, 2_enriched_jira_data, 3_consistency_checked, 4_proportion_applied)

// 5. Indice commit→file (una volta sola, cachato)
CommitIndexService commitIndexService = new CommitIndexService(repoDir);
Map<String, Set<String>> issueToFilesIndex = commitIndexService.buildIssueToFilesIndex();

// 6. Estrazione snapshot Git
GitExtractorOrchestrator gitOrchestrator = new GitExtractorOrchestrator(repoDir, codeOutputDir, false);
List<ReleaseSnapshot> snapshots = gitOrchestrator.extractSnapshots(versions);

// 7. Bugginess labeling
new BugginessOrchestrator(commitIndexService, new BugginessLabelerService())
    .labelSnapshots(snapshots, issues);

// 8. Calcolo metriche
new MetricsOrchestrator(repoDir, versions, gitOrchestrator.getLastResolvedRefs(),
    issues, issueToFilesIndex, config.getMetrics()).computeAll(snapshots);

// 9. Export CSV snapshot
csvExporter.exportSnapshots(snapshots, "OPENJPA", "5_snapshots");
```

---

## Package `gitextractor` — dettagli

### `GitReleaseService`
- Ordina versioni Jira per `releaseDate`, taglia al primo 33% con `Math.ceil(size / 3.0)`.
- Risolve tag Git con match esatto o tag che inizia con `nomeVersione-` (esclusi SNAPSHOT).
- `compensateSkips=false` → il 33% è calcolato sulle versioni con tag trovato.
- Restituisce `LinkedHashMap<String, String>` (jiraName → gitTag).

### `GitFileExtractorService`
- `git ls-tree -r --name-only <gitTag>` per listare file.
- `git show <gitTag>:<path>` per estrarre contenuto.
- Salva in `outputDir/<jiraVersionName>/<path>` (nome Jira, non il gitTag).

### `JavaClassFilterService`
Filtra via path: deve terminare con `.java`, non contenere `/test/` o `/tests/`, non finire con `Test.java` o `Tests.java`.

### `CommitIndexService` (in `gitextractor.support`)
- Una sola invocazione `git log --all --name-only --format=COMMIT:%s`.
- Estrae chiavi issue con regex `[A-Z]+-\d+` dai messaggi.
- Risultato cachato internamente: chiamate successive restituiscono la cache.

### `GitLogStatsService` (in `gitextractor.support`)
- Una sola invocazione `git log --all --numstat --format=COMMIT:%ae|%ci --reverse`.
- Costruisce `Map<String, List<CommitStat>>` (filePath → lista ordinata cronologicamente).
- `getCommitStats(filePath, gitRef)` filtra per data del ref (con cache `filteredStatsCache`).
- `getFullIndex()` usato da `ExpMetric` e `SexpMetric` per lookup globali.

---

## Package `buggyness`

Uno snapshot è **buggy** se esiste almeno un issue tale che:
1. La release è tra le AV dell'issue.
2. Almeno un commit che cita la chiave dell'issue tocca quel classPath.

Il labeling muta gli oggetti `JavaClassSnapshot` in place via `setBuggy(true)`.

---

## Package `metrics` — 19 metriche

| Metrica | Tipo |
|---|---|
| LOC | Puntuale |
| Previous_Release_Code_Smells | Puntuale shiftata (vedi sotto) |
| LOC_Touched | Cumulativa |
| NR | Cumulativa |
| Nfix | Cumulativa |
| Nauth | Cumulativa |
| LOC_Added | Cumulativa |
| Max_LOC_Added | Cumulativa |
| Avg_LOC_Added | Cumulativa |
| Churn | Cumulativa |
| Max_Churn | Cumulativa |
| Avg_Churn | Cumulativa |
| Change_Set_Size | Cumulativa |
| Max_Change_Set | Cumulativa |
| Avg_Change_Set | Cumulativa |
| Age | Cumulativa |
| Weighted_Age | Cumulativa |
| EXP | Cumulativa (media) — esperienza globale autore nella repo |
| SEXP | Cumulativa (media) — esperienza autore sul sottosistema (primo segmento path) |

### Shift dei code smell

I code smell introdotti nella release N impattano N+1, non N. Per ogni release (in ordine cronologico):
1. PMD analizza tutti i file della release → popola cache.
2. `currentReleaseSmells` = cache per tutti i file (non solo il limite %).
3. Ogni snapshot riceve `Previous_Release_Code_Smells` dalla release precedente.
4. `previousReleaseSmells = currentReleaseSmells`.

Prima release → tutti gli snapshot ricevono "0". Classe nuova (non nella release precedente) → "0".

### `CodeSmellsMetric` — parametri
- `batchSize` (default 100): file per invocazione PMD.
- `cpuFraction` (default 0.5): frazione core per thread PMD.
- Rulesets: bestpractices, design, errorprone, codestyle.

---

## Package `csv` — ordine colonne CSV snapshot

`java_class_path, release, LOC, LOC_Touched, NR, Nfix, Nauth, LOC_Added, Max_LOC_Added, Avg_LOC_Added, Churn, Max_Churn, Avg_Churn, Change_Set_Size, Max_Change_Set, Avg_Change_Set, Age, Weighted_Age, EXP, SEXP, Previous_Release_Code_Smells, isBuggy`

Usare sempre `LinkedHashMap` nelle mappe di metriche per preservare l'ordine.

---

## Package `classifier` — Milestone 2

```
classifier/
├── ClassifierOrchestrator              coordina, nessuna logica
├── WekaDatasetService                  carica CSV, rimuove java_class_path e release, setta isBuggy come classe
├── FeatureSelectionBuilderService      costruisce filtro FS
├── BalancingBuilderService             costruisce filtro balancing
├── ClassifierBuilderService            assembla FilteredClassifier + MultiFilter
├── ClassifierEvaluatorService          10×10 CV, accumula metriche, gestisce NaN
├── NpofB20Service                      calcola NPofB20 da eval.predictions()
├── ClassifierResultRowMapperService    formatta righe per CsvWriterService
├── RuntimeEstimatorService             stima wall-clock prima dell'esecuzione
├── SpearmanAttributeEval               ⚙ custom ASEvaluation (Spearman rank)
├── SmoteFilter                         ⚙ custom SimpleBatchFilter (SMOTE k-NN)
├── ClassifierType                      enum (RANDOM_FOREST, NAIVE_BAYES, IBK)
├── FeatureSelectionStrategy            enum (NONE, INFO_GAIN, SPEARMAN, FORWARD_SEARCH, BACKWARD_SEARCH)
└── BalancingStrategy                   enum (NONE, UNDERSAMPLING, OVERSAMPLING, SMOTE)
```

### Decisioni architetturali chiave

- **`FilteredClassifier` + `MultiFilter`**: applica balancing+FS solo sul training fold → nessun data leakage. Ordine: balancing prima, poi FS.
- **Predizioni accumulate**: ogni istanza predetta da un modello che non l'ha vista in training; accumulo su 100 run×fold dà stabilità per NPofB20.
- **Fix double-randomization**: rimossa la pre-randomizzazione manuale; `crossValidateModel` gestisce shuffle internamente.
- **Fix NaN metriche**: `safe(double v)` → 0.0 per NaN/Infinite prima dell'accumulo.
- **`SpearmanAttributeEval`** e **`SmoteFilter`**: custom — Weka stable non li include; SMOTE non è su Maven Central.
- **Parallelismo a due livelli**: `combinations` (ExecutorService) × `randomForestSlots` (setNumExecutionSlots RF). Il loro prodotto non deve superare i core fisici.

### Output classifier

`output/milestone2/OPENJPA_classifier.csv`
Colonne: `Dataset, Classifier, FS, Balancing, Precision, Recall, AUC, Kappa, NPofB20`

---

## Componenti custom

- `SpearmanAttributeEval`: `ASEvaluation` + `AttributeEvaluator` per Spearman rank correlation.
- `SmoteFilter`: `SimpleBatchFilter` SMOTE custom.

---

## Problemi noti e soluzioni

**PMD e Java 26**: PMD 7 non carica i `.class` compilati con Java 26 (major 70) per type resolution. L'analisi sui sorgenti `.java` funziona. Soluzione: `level="OFF"` in logback.xml.

**Branch paralleli OpenJPA**: branch `1.x` e `2.x` in parallelo. Release `1.x` si interpongono tra release `2.x` → alcune classi del branch `2.x` ricevono `Previous_Release_Code_Smells=0`. Comportamento **corretto e atteso**.

**Versioni Jira senza tag Git**: `0.9.0`, `2.0.0-M1`, `2.0.0-M2` esistono su Jira ma non hanno tag Git (progetto era su SVN). Saltate con warning. I ticket che le referenziano sono mantenuti.

**Performance PMD**: `analyzeBatch()` raggruppa tutti i file di una release in sub-batch da `batchSize` file per invocazione. Costo di inizializzazione pagato ~14 volte invece di 15.338. Tempo PMD ora fisso (~279s).

---

## Report LaTeX

Il progetto ha due versioni del report: `latexreport/` (originale, completo) e `latexreportsmart/` (condensato).

### Regole per `latexreportsmart/`

- **Limite**: circa 18 pagine escluse immagini e tabelle (le tabelle vanno in appendice).
- **Scopo**: dimostrare le scelte e motivazioni ingegneristiche, non documentare l'implementazione. Il lettore deve capire il *perché* di ogni decisione, non il *come* nei dettagli.
- **Stile**: non va nel dettaglio di ogni componente. Approfondire solo le decisioni non ovvie, i trade-off, le scelte che distinguono questa soluzione da un'alternativa più semplice. Dettagli implementativi banali si omettono.
- **Sintesi**: quando si deve tagliare, si taglia il descrittivo ("la classe X fa Y") e si mantiene il ragionamento ("X è preferito a Z perché…").

---

## Walk-Forward Validation (non implementata)

Risponde a: "il classificatore migliore cambia in base al numero di release in training?". Richiederebbe: `WalkForwardSplitter`, `WalkForwardEvaluatorService` (train/test diretto, no CV), `WalkForwardOrchestrator`, secondo CSV di output. Documentato nel report (risultati M2, domande aperte).

---

## Domande professore — stato

| Domanda | Risponde il CSV attuale? |
|---|---|
| Quale classificatore è più accurato? | ✅ aggregare per Classifier |
| Cambia in base al dataset? | ✅ abilitare più progetti in config.yaml |
| Cambia in base alla metrica? | ✅ ordinare per ogni colonna metrica |
| Cambia in base al numero di release? | ❌ serve Walk-Forward (non implementata) |
