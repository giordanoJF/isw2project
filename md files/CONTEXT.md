# CONTEXT.md - ISW2 Project (Apache OpenJPA)

Questo file descrive in dettaglio tutto il lavoro svolto sul progetto ISW2, le scelte
architetturali, le convenzioni di codice, le decisioni metodologiche e i TODO aperti.
È pensato per essere letto da Claude Code all'inizio di ogni sessione.

---

## 1. Panoramica del progetto

Il progetto è una pipeline Java (Maven) che costruisce un dataset per la predizione della
bugginess di classi Java in progetti open source Apache. Il progetto analizzato è
**Apache OpenJPA**. Il dataset finale è un CSV con una riga per ogni coppia
(classe Java, release), arricchita con metriche di processo/prodotto e l'etichetta
`isBuggy`.

**Stack tecnologico:**
- Java 26, Maven
- Jackson (deserializzazione JSON da API Jira)
- PMD 7 (analisi statica code smell)
- SLF4J + Logback (logging)
- Git via ProcessBuilder (per operazioni post-clone: log, show, ls-tree)
- JGit 7.1.0 (`org.eclipse.jgit`) - clone automatico del repo al primo avvio (nessuna CLI richiesta)
- Weka 3.8.6 (`weka-stable`) - classificatori ML per Milestone 2
- `jul-to-slf4j` - bridge JUL→SLF4J per sopprimere warning Weka

**Package root:** `com.isw2project`

---

## 2. Struttura dei package

```
com.isw2project/
├── config/           - mappatura config.yaml in oggetti Java
│                       (AppConfig, ClassifierConfig, CrossValidationConfig,
│                        ParallelismConfig, WalkForwardConfig - M2)
├── model/            - modelli dati (Issue, Version, ProjectData,
│                       JavaClassSnapshot, ReleaseSnapshot)
├── downloader/       - download ticket e versioni da API REST Jira
├── enricher/         - arricchimento ticket con OV, FV singola, AV da FV
├── consistency/      - check di consistenza su ticket e versioni
│   └── checks/       - una classe per ogni check
├── proportion/       - stima AV mancanti con tecnica Proportion
├── csv/              - serializzazione dati su file CSV
├── gitextractor/     - estrazione snapshot classi Java da Git
│   └── support/      - utility Git condivise (CommitIndexService,
│                       GitLogStatsService)
├── buggyness/        - labeling isBuggy sugli snapshot
├── metrics/          - calcolo metriche per ogni snapshot
│   └── impl/         - una classe per ogni metrica (implementano Metric)
├── repocloner/       - clone automatico del repository Git (JGit, no CLI)
└── classifier/       - Milestone 2: valutazione classificatori ML (Weka)
```

**Dipendenze tra package (direzione delle chiamate):**
```
main
 ├── downloader
 ├── enricher
 ├── consistency
 ├── proportion
 ├── gitextractor          ← usa gitextractor.support
 ├── buggyness             ← usa gitextractor.support
 ├── metrics               ← usa gitextractor.support
 └── csv
```

`gitextractor.support` è l'unico package usato da altri package (buggyness e metrics).
Non esiste nessuna altra dipendenza trasversale tra package allo stesso livello.

---

## 3. Convenzioni di codice - RISPETTARLE SEMPRE

### Pattern architetturale
Ogni package ha:
- **Un `*Orchestrator`**: coordina senza contenere logica. Chiama solo i service.
  Non ha try-catch. Non lancia eccezioni checked verso il chiamante.
- **Uno o più `*Service`**: contengono la logica implementativa.

### Gestione eccezioni
- Il `Main.java` non ha mai `try-catch` e non dichiara `throws`.
- Le eccezioni checked vengono gestite internamente dagli orchestratori o dai service
  che le generano, con `log.error(...)` e comportamento di fallback (es. lista vuota,
  mappa vuota).
- L'eccezione custom del dominio Git è `GitCommandException` (checked),
  in `com.isw2project.gitextractor`.

### Stile generale
- Commenti in inglese, senza emoji.
- Nessun code smell (verificato con PMD).
- Separazione netta dei ruoli: ogni classe ha una sola responsabilità.
- `LinkedHashMap` dove l'ordine delle chiavi è rilevante (es. colonne CSV).
- Niente `break`/`continue` multipli nei loop (max 1 per loop).
- Metodi pubblici degli orchestratori non dichiarano `throws Exception`.
- I service interni possono dichiarare `throws GitCommandException`.

### Logging
- SLF4J + Logback.
- File `src/main/resources/logback.xml` presente con PMD silenziato:
  `<logger name="net.sourceforge.pmd" level="OFF"/>`.
- Pattern: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger -- %msg%n`

---

## 4. Modelli dati chiave

### `Version.java`
```java
String id, name, description;
boolean released;
String releaseDate;  // formato "yyyy-MM-dd"
```

### `Issue.java`
Modello Jira con inner classes `Fields`, `Status`, `IssueType`, `Priority`.
Campi rilevanti in `Fields`:
- `String created` - data creazione ticket
- `List<Version> affectedVersions` - AV
- `List<Version> fixVersions` - FV
- `Version openingVersion` - calcolata dall'enricher
- `getInjectedVersion()` - metodo getter che restituisce la AV meno recente

### `ProjectData.java`
Raggruppa `projectKey`, `List<Issue>`, `List<Version>` per un progetto.
Le liste sono immutabili (`Collections.unmodifiableList`).

### `JavaClassSnapshot.java`
```java
String classPath;          // path relativo nel repo (es. "openjpa-kernel/src/...")
String release;            // nome versione Jira (es. "1.0.0")
Path codeFilePath;         // path su disco del sorgente estratto
boolean buggy;             // settato da BugginessLabelerService
Map<String, String> metrics; // settato da MetricsOrchestrator
```
- `getCode()` → restituisce `codeFilePath`
- `isBuggy()` / `setBuggy(boolean)`
- `getMetrics()` / `setMetrics(Map)`

### `ReleaseSnapshot.java`
```java
String release;
List<JavaClassSnapshot> classes;
```

---

## 5. Entry point

- **`Milestone1Main.java`** - esegue l'intera pipeline M1 (download → metriche → snapshot CSV).
- **`Milestone2Main.java`** - legge il CSV già prodotto da M1, NON riesegue M1. Installa il
  bridge JUL→SLF4J prima di qualunque altra operazione. Lancia `ClassifierOrchestrator`.
  Run: `mvn exec:java -Dexec.mainClass="com.isw2project.Milestone2Main"`

---

## 5b. Flusso del Milestone1Main.java (ex Main.java)

```java
// 0. Clone automatico del repo (skip se .git/ esiste già)
new RepoCloneOrchestrator(new RepoCloneService()).ensureCloned(config.getGit());

// 1. Download versioni e ticket da Jira
DownloaderOrchestrator downloader = ...
List<ProjectData> downloaded = downloader.download();

// 2. Enriching e check di consistenza (più passi, nell'ordine corretto)
EnricherOrchestrator enricher = ...
ConsistencyOrchestrator consistency = ...
// ... vari passi di enriching e check alternati ...
List<ProjectData> cleaned = ...;

// 3. Proportion per ticket senza AV
ProportionOrchestrator proportion = ...
proportion.applyProportion(cleaned);

// 4. Export CSV ticket e versioni (già esistente prima del lavoro Git)
CsvExporterOrchestrator csvExporter = new CsvExporterOrchestrator(
    config.getCsv(),
    new IssueCsvRowMapperService(),
    new VersionCsvRowMapperService(),
    new SnapshotCsvRowMapperService(),
    new CsvWriterService()
);
csvExporter.export(cleaned, "4_proportion_applied");

// 5. Costruzione indice commit→file (una volta sola, riutilizzato)
File repoDir = new File(config.getGit().getRepoDir());
File codeOutputDir = new File(config.getGit().getCodeOutputDir());
CommitIndexService commitIndexService = new CommitIndexService(repoDir);
Map<String, Set<String>> issueToFilesIndex = commitIndexService.buildIssueToFilesIndex();

// 6. Estrazione snapshot Git
GitExtractorOrchestrator gitOrchestrator =
    new GitExtractorOrchestrator(repoDir, codeOutputDir, false);
List<ReleaseSnapshot> snapshots =
    gitOrchestrator.extractSnapshots(cleaned.getFirst().getVersions());

// 7. Bugginess labeling
new BugginessOrchestrator(commitIndexService, new BugginessLabelerService())
    .labelSnapshots(snapshots, cleaned.getFirst().getIssues());

// 8. Calcolo metriche
MetricsOrchestrator metricsOrchestrator = new MetricsOrchestrator(
    repoDir,
    cleaned.getFirst().getVersions(),
    gitOrchestrator.getLastResolvedRefs(),
    cleaned.getFirst().getIssues(),
    issueToFilesIndex,
    config.getMetrics()
);
metricsOrchestrator.computeAll(snapshots);

// 9. Export CSV snapshot
csvExporter.exportSnapshots(snapshots, "OPENJPA", "5_snapshots");
```

---

## 6. Package `gitextractor` - dettagli

### `GitExtractorOrchestrator`
- Costruttore: `(File repoDir, File outputDir, boolean compensateSkips)`
- `extractSnapshots(List<Version> versions)` - estrae snapshot, gestisce eccezioni internamente
- `getLastResolvedRefs()` - restituisce `Map<String, String>` jiraName→gitTag, usata da MetricsOrchestrator

### `GitReleaseService`
- Riceve `List<Version>` già filtrate da Jira.
- Le ordina per `releaseDate` (formato `yyyy-MM-dd`, ordinabile lessicograficamente).
- Taglia al primo 33% con `Math.ceil(size / 3.0)`.
- Risolve il tag Git per ogni versione Jira con due strategie:
  1. Match esatto del nome
  2. Tag che inizia con `nomeVersione-` escludendo SNAPSHOT
- Versioni senza tag → warning nel log, skip.
- `compensateSkips=false` → il 33% è calcolato sulle versioni con tag trovato.
- Restituisce `LinkedHashMap<String, String>` (jiraName → gitTag), ordine preservato.

### `GitFileExtractorService`
- `extractClassesForRelease(String jiraVersionName, String gitTag)`
- Usa `git ls-tree -r --name-only <gitTag>` per listare file.
- Usa `git show <gitTag>:<path>` per estrarre contenuto.
- Salva in `outputDir/<jiraVersionName>/<path>` (jiraName come nome cartella, NON il gitTag).
- Filtra via `JavaClassFilterService`.

### `JavaClassFilterService`
```java
public boolean isProductionJavaFile(String repoRelativePath) {
    String normalized = repoRelativePath.replace("\\", "/");
    String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
    return normalized.endsWith(".java")
            && !normalized.contains("/test/")
            && !normalized.contains("/tests/")
            && !fileName.endsWith("Test.java")
            && !fileName.endsWith("Tests.java");
}
```

### `gitextractor.support.CommitIndexService`
- **Una sola invocazione git** per tutta l'esecuzione (risultato cachato).
- Esegue `git log --all --name-only --format=COMMIT:%s`.
- Estrae chiavi issue con regex `[A-Z]+-\d+` dai messaggi di commit.
- Restituisce `Map<String, Set<String>>` (issueKey → set di filePath toccati).
- Cache interna: se `buildIssueToFilesIndex()` è già stato chiamato, restituisce il risultato cachato.

### `gitextractor.support.GitLogStatsService`
- **Una sola invocazione git** per tutta l'esecuzione (indice costruito nel costruttore).
- Esegue `git log --all --numstat --format=COMMIT:%ae|%ci --reverse`.
- Costruisce `Map<String, List<CommitStat>>` (filePath → lista CommitStat ordinata cronologicamente).
- `getCommitStats(filePath, gitRef)` → filtra per data del ref (con cache `filteredStatsCache`).
- `getFirstCommitDate(filePath, gitRef)` → primo elemento della lista filtrata.
- `getFullIndex()` → espone l'intero indice read-only; usato da `ExpMetric` e `SexpMetric` per costruire strutture di lookup globali.
- Cache `refDateCache`: la data di ogni gitRef viene risolta con `git log -1 --format=%ci <ref>` una volta sola.
- `CommitStat` inner class: `authorEmail, date (LocalDate), linesAdded, linesDeleted, changeSetSize`.

---

## 7. Package `buggyness` - dettagli

### `BugginessOrchestrator`
- Costruttore: `(CommitIndexService, BugginessLabelerService)`
- `labelSnapshots(List<ReleaseSnapshot>, List<Issue>)` - gestisce eccezioni internamente

### `BugginessLabelerService`
Uno snapshot (classPath, release) è **buggy** se esiste almeno un issue tale che:
1. La release è tra le AV dell'issue.
2. Almeno un commit che cita la chiave dell'issue tocca quel classPath.

Il labeling muta gli oggetti `JavaClassSnapshot` in place via `setBuggy(true)`.

---

## 8. Package `metrics` - dettagli

### Interfaccia `Metric`
```java
String columnName();
String compute(ClassMetricInput input) throws GitCommandException;
```

### `ClassMetricInput`
```java
JavaClassSnapshot snapshot;
String gitRef;        // tag o commit hash
LocalDate releaseDate;
```

### `MetricsOrchestrator`
- Costruttore: `(File repoDir, List<Version>, Map<String,String> jiraNameToGitRef, List<Issue>, Map<String,Set<String>> issueToFilesIndex, MetricsConfig)`
- Costruisce internamente `GitLogStatsService` e tutte le istanze di `Metric`.
- `computeAll(List<ReleaseSnapshot>)` - la percentuale di snapshot viene letta da `MetricsConfig.snapshotPercentage`.
- Ordina le release per `releaseDate` prima del loop (necessario per lo shift dei code smell).
- Gestisce lo **shift dei code smell** (vedi sezione 9).
- Logga il timing per ogni metrica e il tempo totale PMD batch.

### `MetricsComputerService`
- Applica tutte le metriche a un singolo snapshot.
- Traccia il tempo cumulativo per ogni metrica.
- Errori su singole metriche → warning nel log, valore `""` nel CSV (non blocca).
- `logTimingSummary()` → chiamato da MetricsOrchestrator alla fine di `computeAll`.

### Metriche implementate
| Metrica | Tipo | Descrizione |
|---|---|---|
| LOC | Puntuale | Righe non vuote nel file sorgente |
| Previous_Release_Code_Smells | Puntuale shiftata | Violazioni PMD della release precedente |
| LOC_Touched | Cumulativa | Somma righe aggiunte+cancellate su tutti i commit |
| NR | Cumulativa | Numero di revisioni (commit) sul file |
| Nfix | Cumulativa | Ticket con FV=release corrente che toccano il file |
| Nauth | Cumulativa | Autori distinti |
| LOC_Added | Cumulativa | Somma righe aggiunte |
| Max_LOC_Added | Cumulativa | Massimo righe aggiunte in un commit |
| Avg_LOC_Added | Cumulativa | Media righe aggiunte per commit |
| Churn | Cumulativa | Somma righe aggiunte+cancellate |
| Max_Churn | Cumulativa | Massimo churn in un commit |
| Avg_Churn | Cumulativa | Media churn per commit |
| Change_Set_Size | Cumulativa | Somma file committati insieme a questo |
| Max_Change_Set | Cumulativa | Massimo change set in un commit |
| Avg_Change_Set | Cumulativa | Media change set per commit |
| Age | Cumulativa | Giorni dal primo commit del file alla release |
| Weighted_Age | Cumulativa | Age × LOC_Touched |
| EXP | Cumulativa (media) | Media delle commit-day globali dell'autore al momento di ogni commit sul file (esperienza complessiva nella repository) |
| SEXP | Cumulativa (media) | Come EXP ma conteggiata solo sui commit al medesimo sotto-sistema (primo segmento del path) |

### `CodeSmellsMetric` - parametri configurabili
```java
// iniettati dal costruttore via MetricsConfig (config.yaml, sezione metrics.pmd)
private final int batchSize;       // file per invocazione PMD (default: 100)
private final double cpuFraction;  // frazione core per thread PMD (default: 0.5)
private static final String[] RULESETS = { bestpractices, design, errorprone, codestyle };
```
- Usa `analyzeBatch(List<Path>)` chiamato una volta per release da MetricsOrchestrator.
- `getCachedSmells(Path)` espone il valore dalla cache interna per lo shift.
- PMD analizza i file `.java` su disco (non in memoria).

---

## 9. Shift dei code smell - logica dettagliata

**Motivazione:** i code smell introdotti nella release N non impattano N, ma N+1.

**Implementazione in `MetricsOrchestrator.computeAll()`:**
```
previousReleaseSmells = {} (mappa vuota)

per ogni release (in ordine cronologico per releaseDate):
    1. analyzeBatch(tutti i file della release) → PMD popola cache
    2. currentReleaseSmells = { classPath → getCachedSmells(file) }
       per TUTTI i file della release (non solo quelli nel limite %)
    3. per ogni snapshot (fino al limite %):
       a. computerService.compute(input) → calcola tutte le altre metriche
       b. metrics.put("Previous_Release_Code_Smells",
                       previousReleaseSmells.getOrDefault(classPath, "0"))
       c. snapshot.setMetrics(metrics)
    4. previousReleaseSmells = currentReleaseSmells
```

**Caso prima release:** `previousReleaseSmells` è vuota → tutti gli snapshot ricevono "0".

**Caso classe nuova:** se la classe non era nella release precedente,
`previousReleaseSmells` non ha quella chiave → valore "0".

**Caso branch paralleli (es. OpenJPA):** OpenJPA aveva branch `1.x` (manutenzione) e
`2.x` (sviluppo) in parallelo. Ordinando per data, una release `1.x` può interporsi
tra due release `2.x`. Una classe presente solo nel branch `2.x` risulterà assente
nella release `1.x` interposta, ricevendo quindi "0" nella release `2.x` successiva.
Questo è **corretto** rispetto alla logica implementata (release precedente cronologica).

---

## 10. Package `csv` - dettagli

### `CsvExporterOrchestrator`
Costruttore: `(CsvConfig, IssueCsvRowMapperService, VersionCsvRowMapperService, SnapshotCsvRowMapperService, CsvWriterService)`

Metodi:
- `export(List<ProjectData>, String subDir)` - CSV per issues e versioni
- `exportSnapshots(List<ReleaseSnapshot>, String projectKey, String subDir)` - CSV snapshot

### `CsvWriterService` (già esistente nel progetto originale)
```java
void write(String outputDir, String filename, List<Map<String, String>> rows)
```
Deriva l'header dalla `keySet()` della prima riga → usare sempre `LinkedHashMap` per
garantire l'ordine delle colonne.

### `SnapshotCsvRowMapperService`
```java
public Map<String, String> toRow(JavaClassSnapshot snapshot) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("java_class_path", snapshot.getClassPath());
    row.put("release", snapshot.getRelease());
    row.putAll(snapshot.getMetrics());  // metriche in ordine di inserimento
    row.put("isBuggy", String.valueOf(snapshot.isBuggy()));  // sempre ultima
    return row;
}
```

**Ordine colonne nel CSV finale:**
`java_class_path, release, LOC, LOC_Touched, NR, Nfix, Nauth, LOC_Added, Max_LOC_Added,
Avg_LOC_Added, Churn, Max_Churn, Avg_Churn, Change_Set_Size, Max_Change_Set,
Avg_Change_Set, Age, Weighted_Age, EXP, SEXP, Previous_Release_Code_Smells, isBuggy`

---

## 11. Numeri chiave del dataset (OpenJPA, run completo)

| Metrica | Valore |
|---|---|
| Versioni Jira totali | 42 |
| Release nel primo 33% | 14 |
| Release saltate (no tag Git) | 3 (`0.9.0`, `2.0.0-M1`, `2.0.0-M2`) |
| Snapshot totali | 15.338 |
| Snapshot buggy | 1.378 (8,98%) |
| Commit totali | ~7.666 |
| Commit con chiave issue | ~5.286 (68,95%) |
| Issue unici indicizzati | 1.815 |
| Issue senza commit | 64 |

**Timing pipeline completa (hardware medio):**
| Fase | Tempo |
|---|---|
| CommitIndexService + git extraction | ~47s (fisso) |
| GitLogStatsService.buildIndex() | ~11s (fisso) |
| PMD batch (14 release) | ~279s (fisso) |
| Metriche per snapshot | ~0ms |
| **Totale** | **~5-6 minuti** |

---

## 13. Struttura cartelle di output

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/          - CSV scaricati direttamente da Jira
│   │   ├── OPENJPA_issues.csv
│   │   └── OPENJPA_versions.csv
│   ├── 2_enriched_jira_data/     - dopo enrichment OV/AV
│   ├── 3_consistency_checked/    - dopo i check di consistenza
│   ├── 4_proportion_applied/     - dopo Proportion
│   │   ├── OPENJPA_issues.csv
│   │   └── OPENJPA_versions.csv
│   ├── 5_snapshots/
│   │   └── OPENJPA_snapshots.csv - dataset finale con metriche e isBuggy
│   │                               (input per Milestone 2)
│   └── 6_extracted_source/
│       ├── 0.9.6/                - codice estratto per release (nome Jira, non tag Git)
│       │   └── openjpa-kernel/src/.../MyClass.java
│       └── ...
└── milestone2/
    └── OPENJPA_classifier.csv    - risultati ML (una riga per combinazione)
```

---

## 14. Configurazione - config.yaml

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
  issueColumns: { ... }
  versionColumns: { ... }
metrics:
  snapshotPercentage: 1.0   # 1.0 = tutti; 0.1 = 10% per debug rapido
  pmd:
    batchSize: 100           # file per invocazione PMD (RAM vs overhead)
    cpuFraction: 0.5         # frazione core per thread PMD
classifier:
  inputCsv: output/milestone1/5_snapshots/OPENJPA_snapshots.csv
  outputDir: output/milestone2
  datasetName: OPENJPA
```

---

## 15. Package `classifier` - Milestone 2 (implementata)

### Classi principali

```
com.isw2project.classifier/
├── ClassifierOrchestrator              - coordina il flusso, nessuna logica
├── WekaDatasetService                  - carica CSV, rimuove java_class_path e release,
│                                         setta isBuggy come classe
├── FeatureSelectionBuilderService      - costruisce filtro FS (AttributeSelection)
├── BalancingBuilderService             - costruisce filtro balancing
├── ClassifierBuilderService            - assembla FilteredClassifier + MultiFilter
├── ClassifierEvaluatorService          - 10×10 CV, accumula metriche, gestisce NaN
├── NpofB20Service                      - calcola NPofB20 da eval.predictions()
├── ClassifierResultRowMapperService    - formatta righe per CsvWriterService
├── InteractiveParallelismConfigurator  - prompt a runtime per thread count
├── RuntimeEstimatorService             - stima wall-clock prima dell'esecuzione
├── SpearmanAttributeEval               - ⚙ custom ASEvaluation (Spearman rank)
├── SmoteFilter                         - ⚙ custom SimpleBatchFilter (SMOTE k-NN)
├── ClassifierType                      - enum (RANDOM_FOREST, NAIVE_BAYES, IBK)
├── FeatureSelectionStrategy            - enum (NONE, INFO_GAIN, SPEARMAN,
│                                         FORWARD_SEARCH, BACKWARD_SEARCH)
└── BalancingStrategy                   - enum (NONE, UNDERSAMPLING, OVERSAMPLING, SMOTE)
```

### Decisioni architetturali chiave

- **`FilteredClassifier` + `MultiFilter`**: applica balancing+FS *solo* sul training fold
  di ogni split CV → nessun data leakage. Ordine: balancing prima, poi FS.
- **`SpearmanAttributeEval`** e **`SmoteFilter`** sono custom: Weka stable non li include.
  SMOTE non è su Maven Central, quindi implementazione propria come `SimpleBatchFilter`.
- **Parallelismo a due livelli**: `combinations` (thread per combinazione, via `ExecutorService`)
  e `randomForestSlots` (thread interni RF, via `setNumExecutionSlots`). I due si moltiplicano
  → il loro prodotto non deve superare i core fisici.
- **`ParallelismConfig`** accetta `"auto"`, interi (`"8"`) e percentuali (`"50%"`).
  `interactive: true` attiva il prompt a runtime con `InteractiveParallelismConfigurator`.
- **Fix double-randomization**: `crossValidateModel` gestisce shuffle internamente; rimossa
  la pre-randomizzazione manuale che produceva fold errati.
- **Fix NaN metriche**: `safe(double v)` → 0.0 per NaN/Infinite prima dell'accumulo.
- **Warning Weka all'avvio**: soppressi con `--enable-native-access=ALL-UNNAMED` (exec plugin)
  e bridge `jul-to-slf4j` + `com.github.fommil` a `OFF` in logback.xml.

### Output

`output/milestone2/OPENJPA_classifier.csv`
Colonne: `Dataset, Classifier, FS, Balancing, Precision, Recall, AUC, Kappa, NPofB20`
27 righe default (3 clf × 3 FS × 3 bal, SMOTE disabilitato). Con SMOTE abilitato: 36 righe.

### Configurazione (`config.yaml`, sezione `classifier`)

- `classifiers`, `featureSelection`, `balancing` - liste abilitabili/disabilitabili
- `featureSelection`: FORWARD_SEARCH e BACKWARD_SEARCH commentati (1-3h per combinazione)
- `balancing`: SMOTE commentato (più lento dell'oversampling)
- `parallelism.interactive`: true → prompt; false → legge combinations/randomForestSlots
- **Raccomandazione portatile**: `combinations: "50%"`, `randomForestSlots: "1"`
- **Raccomandazione PC fisso**: `combinations: "auto"`, `randomForestSlots: "auto"`
- **RuntimeEstimatorService** - calibrato su run reale: RF=757s, IBk=227s, NB=6s (NONE FS, NONE bal);
  UNDERSAMPLING ×0.20, OVERSAMPLING ×1.65, SMOTE ×2.0. Stima default 27 comb: ~2h22m seq → ~35m con 4 thread.

### Domande professore - stato

| Domanda | Risponde il CSV attuale? |
|---|---|
| Quale classificatore è più accurato? | ✅ aggregare per Classifier nel CSV |
| Cambia in base al dataset? | ✅ abilitare più progetti in config.yaml |
| Cambia in base alla metrica? | ✅ ordinare per ogni colonna metrica |
| Cambia in base al **numeroRelease**? | ❌ serve Walk-Forward Validation (non implementata) |

La Walk-Forward richiederebbe: estrarre etichette release prima della rimozione colonna,
`WalkForwardSplitter`, `WalkForwardEvaluatorService` (train/test diretto, no CV),
`WalkForwardOrchestrator` con secondo CSV output. Documentato in sezione 13 del report.

---

## 16. TODO e lavori futuri

- **Walk-Forward Validation** (Milestone 2 - non implementata): per rispondere a
  "il miglior classificatore cambia in base al numero di release usate in training?".
- **Milestone 3**: analisi impatto code smell sulla bugginess.
- **Milestone 4**: refactoring automatico dei code smell.
- **Report LaTeX**: il progetto Overleaf è già strutturato con placeholder per M2-M4
  in `sections/metodologia_m*.tex` e `sections/risultati_m*.tex`.
- Il parametro `compensateSkips=false` in `GitExtractorOrchestrator` è configurabile:
  se `true`, le release saltate per mancanza di tag Git vengono compensate estendendo
  la finestra oltre il 33%.

---

## 17. Problemi noti e soluzioni adottate

### PMD e Java 26
PMD 7 non riesce a caricare i `.class` compilati con Java 26 (major version 70) per la
type resolution via ASM. L'analisi sui sorgenti `.java` funziona comunque correttamente.
Soluzione: `level="OFF"` in logback.xml per silenziare i log interni di PMD.

### Branch paralleli OpenJPA
OpenJPA manteneva branch `1.x` e `2.x` in parallelo. Alcune release `1.x` si interpongono
cronologicamente tra release `2.x`, causando che alcune classi del branch `2.x` abbiano
`Previous_Release_Code_Smells=0` quando la release precedente è una `1.x` che non le contiene.
Questo è il comportamento **corretto e atteso**.

### Versioni Jira senza tag Git
`0.9.0`, `2.0.0-M1`, `2.0.0-M2` esistono su Jira ma non hanno tag Git (il progetto era
ancora su SVN). Vengono saltate con warning nel log. I ticket che le referenziano vengono
mantenuti nel dataset.

### Performance PMD
PMD era il collo di bottiglia principale (170ms/snapshot con istanza separata per file).
Soluzione: `analyzeBatch()` raggruppa tutti i file di una release in sub-batch da
`batchSize` file per invocazione PMD (default 100, configurabile in `metrics.pmd.batchSize`),
pagando il costo di inizializzazione ~14 volte invece di 15.338.
Il tempo PMD è ora fisso (~279s) indipendentemente dal numero di snapshot.

### JNI native access warning (Milestone 2 / Weka)
All'avvio di Milestone 2, `jniloader` (dipendenza transitiva di Weka/netlib) chiama
`System::load` da un modulo unnamed, producendo un warning JVM in Java 24+. Fix:
flag `--enable-native-access=ALL-UNNAMED` aggiunto in `.mvn/jvm.config` (letto
automaticamente dal launcher Maven). Da IntelliJ il flag va inserito manualmente in
**VM options** della run configuration (`Edit Configurations...`).

---

## 18. Prima Esecuzione di Test - Risultati e Osservazioni (OpenJPA)

> Run preliminare: 27 combinazioni (3 clf × 3 FS × 3 bal, SMOTE e wrapper FS disabilitati).
> Run futuri: SMOTE (36 comb.), wrapper FS, altri dataset (BOOKKEEPER, KAFKA).

### Findings chiave

- **Feature Selection = no-op**: InfoGain e Spearman con `threshold=0.0` non eliminano
  nessuna feature su OpenJPA - tutte le ~19 feature hanno InfoGain > 0. I risultati di No FS,
  InfoGain e Spearman sono identici alla terza decimale per ogni coppia (Classifier, Balancing).
  Il wrapper FS potrebbe trovare sottoinsiemi più piccoli ma non è stato eseguito.

- **Kappa < 0.6 su tutto il run** (massimo: RF + Oversampling = 0.59): è un limite strutturale,
  non un difetto. Con imbalance al ~9%, per ottenere Kappa=0.6 servono >93% di predizioni
  corrette in assoluto (vedi calcolo P_e nel report §14.2). Kappa=0.58-0.59 per RF è nella
  fascia alta della letteratura di defect prediction (Hall 2012: tipico 0.2-0.5).

- **AUC 0.94 e Kappa 0.58 coesistono senza contraddizione**: AUC misura il ranking
  (threshold-free), Kappa misura le predizioni binarie a soglia 0.5. Con 9% di imbalance la
  soglia ottimale non è 0.5 → Kappa è depresso anche con un modello discriminativamente ottimo.

- **Undersampling: Recall↑ ma Kappa↓**: RF Under → Recall 0.86 ma Kappa 0.37 (vs 0.58 No Bal).
  I FP esplodono (Precision 0.73→0.31); Kappa penalizza la sovra-predizione. Oversampling è
  il miglior compromesso per RF (Precision 0.61, Recall 0.66, Kappa 0.59).

- **NaiveBayes insensibile al balancing**: Kappa fisso a 0.26 con tutte e tre le strategie.
  L'assumption violation domina sull'imbalance; il balancing modifica la prior ma non risolve
  le correlazioni tra feature.

- **NPofB20 di RF = 0.88** (No Bal): il top 20% dei file per risk score cattura l'88% dei bug.
  Baseline casuale = 0.20. NB e IBk si fermano a ~0.54-0.56 (scarsa calibrazione posterior).

### Valori di riferimento del run

| Classifier | Bal | Precision | Recall | AUC | Kappa | NPofB20 |
|---|---|---|---|---|---|---|
| RF | None | 0.73 | 0.52 | 0.94 | 0.58 | 0.88 |
| RF | Under | 0.31 | 0.86 | 0.91 | 0.37 | 0.80 |
| RF | Over | 0.61 | 0.66 | 0.94 | 0.59 | 0.87 |
| NB | None | 0.35 | 0.29 | 0.79 | 0.26 | 0.54 |
| NB | Under | 0.34 | 0.32 | 0.76 | 0.26 | 0.53 |
| NB | Over | 0.34 | 0.32 | 0.78 | 0.26 | 0.54 |
| IBk | None | 0.53 | 0.51 | 0.75 | 0.47 | 0.56 |
| IBk | Under | 0.26 | 0.81 | 0.79 | 0.30 | 0.57 |
| IBk | Over | 0.47 | 0.58 | 0.77 | 0.46 | 0.61 |
