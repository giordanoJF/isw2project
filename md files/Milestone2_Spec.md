# Milestone 2 – Classifier Accuracy: Specifiche e Guida Step-by-Step

## Obiettivo

Confrontare l'accuratezza di **tre classificatori** (RandomForest, NaiveBayes, IBk) sul dataset prodotto dalla Milestone 1, applicando combinazioni di **Feature Selection** e **Balancing**, e raccogliere i risultati in un file CSV strutturato.

Le metriche da raccogliere per ogni combinazione sono:
- **Precision**
- **Recall**
- **AUC** (Area Under the ROC Curve)
- **Kappa**
- **NPofB20** *(opzionale ma implementata)*

La validazione utilizzata è **10 times 10-fold cross-validation**.

---

## Output Atteso

Il file CSV finale ha la struttura seguente (una riga per ogni combinazione Classificatore × FS × Balancing):

```
Dataset,Classifier,FS,Balancing,Precision,Recall,AUC,Kappa,NPofB20
Argo,RandomForest,No,No,0.04,0.42,0.73,0.72,0.33
Argo,NaiveBayes,No,No,0.58,0.7,0.93,0.12,0.57
...
```

Le combinazioni totali per dataset sono **12** (3 classificatori × 2 opzioni FS × 2 opzioni Balancing).

---

## Dipendenze Maven/Gradle

```xml
<!-- Weka (versione stabile) -->
<dependency>
    <groupId>nz.ac.waikato.cms.weka</groupId>
    <artifactId>weka-stable</artifactId>
    <version>3.8.6</version>
</dependency>
```

> **Perché Weka?** Weka fornisce un'API Java completa per caricare dataset, applicare filtri (balancing, feature selection) e addestrare/valutare classificatori in modo programmatico, senza usare la GUI.

---

## Step 1 – Lettura e Conversione del CSV in formato ARFF

### Cosa fa
Il CSV prodotto dalla Milestone 1 viene caricato e convertito nel formato **ARFF** (Attribute-Relation File Format), che è il formato nativo di Weka. ARFF descrive esplicitamente il tipo di ogni attributo (numerico, nominale, ecc.) e la relazione tra di essi.

### Perché
Weka non legge direttamente i CSV in modo affidabile per tutti i tipi di attributo. La conversione in ARFF garantisce che:
- I tipi di attributo siano corretti (es. `NUMERIC`, `{yes,no}`)
- L'attributo target (classe) sia riconosciuto come tale
- Non ci siano ambiguità sul separatore o sull'encoding

### Come

```java
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;

// 1. Carica il CSV
CSVLoader loader = new CSVLoader();
loader.setSource(new File("dataset_milestone1.csv"));
Instances data = loader.getDataSet();

// 2. Imposta l'attributo target (l'ultimo attributo, o quello corrispondente a "buggy")
//    Se il target non è l'ultimo, usa:
//    data.setClassIndex(data.attribute("buggy").index());
data.setClassIndex(data.numAttributes() - 1);

// 3. (Opzionale) Salva come ARFF per verifica
ArffSaver saver = new ArffSaver();
saver.setInstances(data);
saver.setFile(new File("dataset.arff"));
saver.writeBatch();
```

> **Nota:** L'attributo classe deve essere **nominale** (es. `{yes,no}` o `{true,false}`). Se nel CSV è rappresentato come intero (0/1), va convertito prima o tramite filtro `NumericToNominal`.

---

## Step 2 – Struttura del Progetto e Loop Principale

### Cosa fa
Il codice itera su tutte le **combinazioni** di classificatore, feature selection e balancing, eseguendo la valutazione per ciascuna.

### Perché
Avere un loop strutturato evita duplicazione di codice e garantisce che ogni combinazione venga valutata con le stesse istanze e la stessa procedura.

### Come

```java
String[] classifiers = {"RandomForest", "NaiveBayes", "IBk"};
boolean[] fsOptions    = {false, true};   // No FS, Yes FS
boolean[] balOptions   = {false, true};   // No Balancing, Yes Balancing

List<String[]> results = new ArrayList<>();
results.add(new String[]{"Dataset","Classifier","FS","Balancing",
                          "Precision","Recall","AUC","Kappa","NPofB20"});

for (String classifierName : classifiers) {
    for (boolean useFS : fsOptions) {
        for (boolean useBal : balOptions) {
            double[] metrics = evaluate(data, classifierName, useFS, useBal);
            results.add(new String[]{
                datasetName,
                classifierName,
                useFS  ? "Yes" : "No",
                useBal ? "Yes" : "No",
                fmt(metrics[0]), fmt(metrics[1]),
                fmt(metrics[2]), fmt(metrics[3]), fmt(metrics[4])
            });
        }
    }
}
```

---

## Step 3 – Feature Selection (Filtro)

### Cosa fa
Seleziona un sottoinsieme degli attributi più rilevanti per la predizione, riducendo il rumore e la dimensionalità.

### Perché (dalla lezione Feature Selection)
- **Riduce il costo di apprendimento** eliminando attributi irrilevanti o ridondanti.
- **Migliora le prestazioni** di certi classificatori (es. NaiveBayes soffre molto di attributi ridondanti).
- Si usa un approccio **Filter** (indipendente dal classificatore) che è veloce e general-purpose, ideale quando il numero di attributi è elevato.
- Il criterio usato è **Information Gain** (IG): misura quanto ogni attributo riduce l'incertezza (entropia) sulla classe target. Un IG alto = attributo molto informativo.

### Come

```java
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.filters.supervised.attribute.AttributeSelection;

private Instances applyFeatureSelection(Instances data) throws Exception {
    AttributeSelection filter = new AttributeSelection();

    // Evaluator: Information Gain
    InfoGainAttributeEval evaluator = new InfoGainAttributeEval();

    // Search method: Ranker (ordina attributi per IG, taglia i peggiori)
    Ranker ranker = new Ranker();
    ranker.setThreshold(0.0); // taglia attributi con IG <= 0 (irrilevanti)
    // In alternativa: ranker.setNumToSelect(10); per selezionare esattamente N attributi

    filter.setEvaluator(evaluator);
    filter.setSearch(ranker);
    filter.setInputFormat(data);

    return Filter.useFilter(data, filter);
}
```

> **Nota:** La feature selection viene applicata **solo al training set** (dentro la cross-validation) per evitare data leakage. Weka gestisce questo automaticamente se si usa `FilteredClassifier` (vedi Step 5).

---

## Step 4 – Balancing

### Cosa fa
Corregge lo sbilanciamento tra classi (es. pochi file buggy rispetto a quelli non-buggy) prima di addestrare il classificatore.

### Perché (dalla lezione Balancing)
Nei dataset di software engineering, la classe minoritaria (buggy=yes) è spesso molto rara. I classificatori standard tendono a ignorarla, producendo alta accuracy ma bassa recall sulla classe di interesse. Il balancing forza il modello a "prestare attenzione" alla classe minoritaria.

Vengono supportate due tecniche:

#### Oversampling (Resample)
Duplica istanze della classe minoritaria. Il parametro `sampleSizePercent` si calcola come:
```
Y = 100 * (majority - minority) / minority
```
In Weka: `weka.filters.supervised.instance.Resample` con `biasToUniformClass=1.0` e `noReplacement=false`.

#### SMOTE (opzionale, migliore dell'oversampling semplice)
Genera istanze **sintetiche** della classe minoritaria interpolando tra istanze reali nello spazio delle feature. È più robusto dell'oversampling semplice perché non duplica esattamente le stesse istanze.

### Come (Oversampling con Resample)

```java
import weka.filters.supervised.instance.Resample;

private Instances applyBalancing(Instances data) throws Exception {
    // Calcola il numero di istanze per classe
    int majority = 0, minority = Integer.MAX_VALUE;
    for (int i = 0; i < data.numClasses(); i++) {
        int count = (int) data.attributeStats(data.classIndex())
                              .nominalCounts[i];
        if (count > majority) majority = count;
        if (count < minority) minority = count;
    }

    double sampleSizePercent = 100.0 * (majority - minority) / minority;

    Resample resample = new Resample();
    resample.setBiasToUniformClass(1.0);
    resample.setNoReplacement(false);
    resample.setSampleSizePercent(sampleSizePercent + 100); // +100 perché include anche le esistenti
    resample.setInputFormat(data);

    return Filter.useFilter(data, resample);
}
```

> **Nota:** Come per la feature selection, il balancing va applicato **solo al training set** per non "inquinare" il test set con dati artificiali. Usare `FilteredClassifier` risolve questo automaticamente.

---

## Step 5 – Costruzione del Classificatore (con FilteredClassifier)

### Cosa fa
`FilteredClassifier` è un wrapper Weka che applica i filtri **internamente** durante la cross-validation, garantendo che vengano applicati solo al fold di training e mai al fold di test.

### Perché
Se si applicano balancing e feature selection **prima** della cross-validation sull'intero dataset, si introduce **data leakage**: il modello "vede" informazioni dal test set durante il training, producendo stime di accuratezza ottimisticamente distorte. `FilteredClassifier` risolve questo problema.

### Come

```java
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.filters.MultiFilter;

private Classifier buildClassifier(String name, boolean useFS, boolean useBal)
        throws Exception {

    // 1. Seleziona il classificatore base
    Classifier base;
    switch (name) {
        case "RandomForest": base = new RandomForest(); break;
        case "NaiveBayes":   base = new NaiveBayes();   break;
        case "IBk":          base = new IBk();           break;
        default: throw new IllegalArgumentException("Unknown: " + name);
    }

    // 2. Se non servono filtri, restituisce il classificatore puro
    if (!useFS && !useBal) return base;

    // 3. Costruisce la lista di filtri da applicare
    List<Filter> filters = new ArrayList<>();

    if (useBal) {
        Resample resample = new Resample();
        resample.setBiasToUniformClass(1.0);
        resample.setNoReplacement(false);
        resample.setSampleSizePercent(130.3); // esempio; va calcolato sul dataset
        filters.add(resample);
    }

    if (useFS) {
        AttributeSelection fsFilter = new AttributeSelection();
        fsFilter.setEvaluator(new InfoGainAttributeEval());
        Ranker ranker = new Ranker();
        ranker.setThreshold(0.0);
        fsFilter.setSearch(ranker);
        filters.add(fsFilter);
    }

    // 4. Combina filtri con MultiFilter
    MultiFilter multiFilter = new MultiFilter();
    multiFilter.setFilters(filters.toArray(new Filter[0]));

    // 5. Avvolge tutto in FilteredClassifier
    FilteredClassifier fc = new FilteredClassifier();
    fc.setFilter(multiFilter);
    fc.setClassifier(base);
    return fc;
}
```

---

## Step 6 – Valutazione: 10 Times 10-Fold Cross-Validation

### Cosa fa
Esegue la cross-validation 10 volte (ciascuna con seed diverso) con 10 fold, e fa la media dei risultati. Questo riduce la varianza della stima delle metriche.

### Perché
Un singolo run di 10-fold CV può dare risultati fortemente influenzati da come il dataset è stato diviso casualmente. Ripetere 10 volte con seed diversi produce una stima più stabile e affidabile.

### Come

```java
import weka.classifiers.Evaluation;
import java.util.Random;

private double[] evaluate(Instances data, String classifierName,
                           boolean useFS, boolean useBal) throws Exception {

    double sumPrecision = 0, sumRecall = 0, sumAUC = 0,
           sumKappa = 0, sumNPofB = 0;
    int RUNS = 10, FOLDS = 10;

    for (int run = 0; run < RUNS; run++) {
        Instances shuffled = new Instances(data);
        shuffled.randomize(new Random(run)); // seed diverso per ogni run
        shuffled.stratify(FOLDS);            // mantiene proporzione delle classi

        Evaluation eval = new Evaluation(shuffled);
        Classifier clf = buildClassifier(classifierName, useFS, useBal);

        eval.crossValidateModel(clf, shuffled, FOLDS, new Random(run));

        // Classe positiva = indice 1 (tipicamente "yes" o "true")
        int posClass = 1;
        sumPrecision += eval.precision(posClass);
        sumRecall    += eval.recall(posClass);
        sumAUC       += eval.areaUnderROC(posClass);
        sumKappa     += eval.kappa();
        sumNPofB     += computeNPofB20(eval, shuffled);
    }

    return new double[]{
        sumPrecision / RUNS,
        sumRecall    / RUNS,
        sumAUC       / RUNS,
        sumKappa     / RUNS,
        sumNPofB     / RUNS
    };
}
```

---

## Step 7 – Calcolo di NPofB20

### Cosa fa
**NPofB20** (Normalized Proportion of Bugs in the top 20% of code) misura quante bug vengono trovate ispezionando solo il 20% del codice, ordinato per probabilità decrescente di contenere bug.

### Perché
È una metrica orientata al **costo di ispezione**: un buon classificatore deve concentrare i file buggy nelle prime posizioni della lista ordinata per rischio, così i tester possono trovare il massimo numero di bug con il minimo sforzo.

### Come

```java
private double computeNPofB20(Evaluation eval, Instances data) throws Exception {
    // Raccoglie le probabilità predette per la classe positiva
    // e l'etichetta reale per ogni istanza del test set
    // (in cross-validation, Weka accumula le predizioni)

    Classifier clf = ...; // classificatore già addestrato
    // Nota: in cross-val, usare eval.predictions() dopo crossValidateModel

    List<double[]> predictions = new ArrayList<>();
    for (Prediction pred : eval.predictions()) {
        NominalPrediction np = (NominalPrediction) pred;
        double prob = np.distribution()[1]; // probabilità classe positiva
        double actual = np.actual();
        predictions.add(new double[]{prob, actual});
    }

    // Ordina per probabilità decrescente
    predictions.sort((a, b) -> Double.compare(b[0], a[0]));

    int totalInstances = predictions.size();
    int top20count = (int) Math.ceil(totalInstances * 0.20);
    int totalBugs = (int) predictions.stream().filter(p -> p[1] == 1.0).count();

    if (totalBugs == 0) return 0.0;

    // Conta bug trovate nel top 20%
    long bugsInTop20 = predictions.subList(0, top20count)
                                   .stream()
                                   .filter(p -> p[1] == 1.0)
                                   .count();

    return (double) bugsInTop20 / totalBugs;
}
```

> **Nota:** Per abilitare le predizioni in `Evaluation`, chiamare `eval.crossValidateModel(...)` con l'output buffer attivato: `eval = new Evaluation(data); eval.setDiscardPredictions(false);` (default in Weka 3.8+).

---

## Step 8 – Scrittura del CSV di Output

### Cosa fa
Scrive il file CSV finale con tutte le righe raccolte nel loop principale.

### Come

```java
import java.io.FileWriter;
import java.io.PrintWriter;

try (PrintWriter pw = new PrintWriter(new FileWriter("results_milestone2.csv"))) {
    for (String[] row : results) {
        pw.println(String.join(",", row));
    }
}

// Helper per formattare a 2 decimali
private String fmt(double v) {
    return String.format("%.2f", v);
}
```

---

## Riepilogo del Flusso Completo

```
CSV Milestone 1
      │
      ▼
[Step 1] Conversione CSV → Instances (ARFF)
      │
      ▼
[Step 2] Loop: Classifier × FS × Balancing  (12 combinazioni)
      │
      ├─[Step 3] Feature Selection (InfoGain + Ranker) ─────────────┐
      ├─[Step 4] Balancing (Resample / SMOTE) ──────────────────────┤
      └─[Step 5] FilteredClassifier (filtri applicati solo su train) ┘
                        │
                        ▼
             [Step 6] 10 × 10-fold Cross-Validation
                        │
                        ▼
             [Step 7] Calcolo metriche:
                  Precision, Recall, AUC, Kappa, NPofB20
                        │
                        ▼
             [Step 8] Scrittura CSV di output
```

---

## Note Finali

- **Ordine dei filtri:** Applicare prima il balancing e poi la feature selection. Il balancing altera la distribuzione, quindi la feature selection deve operare sulla distribuzione bilanciata.
- **Attributo classe:** Verificare sempre che sia impostato correttamente (`setClassIndex`) e che sia di tipo nominale.
- **Stratify:** Usare `shuffled.stratify(FOLDS)` per garantire che ogni fold abbia la stessa proporzione di classi — fondamentale con dataset sbilanciati.
- **sampleSizePercent del Resample:** Va calcolato dinamicamente sul training set all'interno di ogni fold, non sul dataset intero. Con `FilteredClassifier`, Weka lo gestisce automaticamente se si configura il filtro correttamente.
- **NPofB20 e predizioni:** Assicurarsi che `eval.setDiscardPredictions(false)` sia impostato (o che sia il default) per poter accedere a `eval.predictions()` dopo la cross-validation.
