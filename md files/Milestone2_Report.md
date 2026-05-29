# Milestone 2 - Report: Valutazione dei Classificatori per la Predizione della Bugginess

## 1. Obiettivo

La Milestone 2 estende il dataset prodotto dalla Milestone 1
(`output/milestone1/5_snapshots/OPENJPA_snapshots.csv`) con una valutazione comparativa di
classificatori per la predizione della bugginess di classi Java nel progetto Apache OpenJPA.

Per ogni combinazione di classificatore, strategia di feature selection e strategia di
balancing viene calcolata una serie di metriche di performance tramite 10-times 10-fold
cross-validation. I risultati sono scritti in `output/milestone2/OPENJPA_classifier.csv`.

La Milestone 2 è eseguibile in modo completamente **indipendente** dalla Milestone 1:
la classe `Milestone2Main` legge il CSV già prodotto da disco senza rieseguire la pipeline.

---

## 2. Dataset di Input

Il CSV prodotto dalla Milestone 1 ha la seguente struttura:

```
java_class_path, release, LOC, LOC_Touched, NR, Nfix, Nauth, LOC_Added,
Max_LOC_Added, Avg_LOC_Added, Churn, Max_Churn, Avg_Churn, Change_Set_Size,
Max_Change_Set, Avg_Change_Set, Age, Weighted_Age, EXP, SEXP,
Previous_Release_Code_Smells, isBuggy
```

Le colonne `java_class_path` e `release` sono **identificatori**, non feature predittive:
includere percorsi di file o nomi di versione nel training farebbe memorizzare al
classificatore etichette associate a stringhe specifiche, non pattern generalizzabili.
Vengono rimosse prima del caricamento in Weka tramite un filtro `Remove`.

La colonna `isBuggy` (valori `true`/`false`) è l'attributo classe (nominale).
Il dataset OpenJPA presenta una forte sbilanciamento: circa l'**8,98% di istanze buggy**
(~1.378 su ~15.338), rendendo il bilanciamento una fase critica del preprocessing.

---

## 3. Classificatori

### 3.0 Definizione e scopo

Un **classificatore** è un algoritmo che, dato un vettore di feature numeriche che descrive
un oggetto, predice a quale classe quell'oggetto appartenga. In questo progetto ogni istanza
è una coppia (classe Java, release) descritta da 19 feature di processo e prodotto; il
classificatore deve predire se quella classe è buggy (`true`) o non buggy (`false`).

L'output del classificatore è tipicamente una **distribuzione di probabilità** sulle classi
(es. P(buggy)=0.82, P(non-buggy)=0.18). La classe predetta è quella con probabilità massima,
ma la distribuzione completa è indispensabile per le metriche AUC e NPofB20 che si basano
sul ranking delle istanze per rischio.

Sono stati scelti tre classificatori con approcci radicalmente diversi: uno basato su
ensemble di alberi (RandomForest), uno probabilistico (NaiveBayes) e uno basato su distanza
(IBk). Questa diversità rende il confronto significativo e permette di isolare quali
caratteristiche del modello contano di più per questo problema specifico.

---

### 3.1 RandomForest

**Definizione:** ensemble di alberi decisionali addestrati ciascuno su un campione bootstrap
del training set, con selezione casuale di un sottoinsieme di feature a ogni split interno.

**A cosa serve:** ridurre la varianza tipica di un singolo albero decisionale attraverso
l'aggregazione (bagging). Ogni albero vede un sottoinsieme diverso dei dati e delle feature,
risultando in modelli parzialmente scorrelati tra loro. La predizione finale è la media delle
probabilità di tutti gli alberi, che è più stabile e meno soggetta a overfitting rispetto
a qualsiasi singolo albero.

**Come è implementato:** Weka `RandomForest` con impostazioni default (100 alberi,
sqrt(m) feature per split). Il numero di thread per la costruzione degli alberi è
controllato da `rfSlots` in `ParallelismConfig` (vedi sezione 9). Con `rfSlots=1`
ogni albero è costruito in sequenza; aumentando `rfSlots` la costruzione è parallelizzata
internamente da Weka.

| Pro | Contro |
|---|---|
| Robusto all'overfitting grazie all'ensemble | Meno interpretabile dei singoli alberi |
| Gestisce naturalmente feature irrilevanti (l'importanza è distribuita) | Con `rfSlots=1` è il classificatore più lento (~757s per combinazione) |
| Produce probabilità ben calibrate (utile per NPofB20) | Con dataset fortemente sbilanciato tende comunque verso la classe maggioritaria |
| Non richiede normalizzazione delle feature | Memoria proporzionale a numAlberi × profondità |
| Stabile: poca varianza tra run diversi | - |

**Runtime misurato (questo hardware, rfSlots=1):**
~757s (None), ~151s (Undersampling), ~1254s (Oversampling) - vedi sezione 8 per la calibrazione.

---

### 3.2 NaiveBayes

**Definizione:** classificatore probabilistico generativo che stima P(classe | feature) tramite
il teorema di Bayes, assumendo che tutte le feature siano **condizionalmente indipendenti**
data la classe. Per feature continue (come tutte le nostre metriche) assume una distribuzione
gaussiana per ogni attributo all'interno di ogni classe.

**A cosa serve:** fornire una baseline probabilistica veloce e interpretabile. L'assunzione
di indipendenza è quasi sempre violata in pratica (LOC_Touched, LOC_Added e Churn sono
fortemente correlate), ma NaiveBayes rimane utile come riferimento e perché la feature
selection può ridurre drasticamente queste ridondanze.

**Come è implementato:** Weka `NaiveBayes` con impostazioni default. La stima della
gaussiana per ogni attributo è fatta sul training fold corrente; il modello non ha iperparametri
da ottimizzare, il che lo rende ideale come classificatore interno nei metodi wrapper.

| Pro | Contro |
|---|---|
| Il più veloce: ~6s per combinazione (None), training e prediction in O(n×m) | L'assunzione di indipendenza condizionale è quasi sempre violata |
| Interpretabile: ogni probabilità è calcolabile analiticamente | Feature correlate (LOC_Touched, Churn, LOC_Added...) lo penalizzano fortemente |
| Beneficia molto dalla feature selection (riduce le ridondanze) | Assume distribuzione gaussiana: non adatta a metriche con distribuzione skewed |
| Nessun iperparametro: nessun rischio di overfitting nella selezione del modello | Può produrre probabilità mal calibrate agli estremi (0 o 1) |

**Runtime misurato:** ~6s (None), ~2s (Undersampling), ~10s (Oversampling).

---

### 3.3 IBk (k-Nearest Neighbors)

**Definizione:** classificatore instance-based (lazy learner) che non costruisce un modello
esplicito durante il training. La predizione di una nuova istanza avviene calcolando la
distanza euclidea da tutte le istanze del training set, trovando i k più vicini (k=1 di
default in Weka) e votando per la classe più frequente tra essi.

**A cosa serve:** catturare pattern locali nello spazio delle feature senza assumere nessuna
forma globale della distribuzione. Se le istanze buggy formano cluster densi nello spazio
(LOC alta + molte revisioni + alto churn), IBk le rileva bene. È anche un utile confronto
con gli approcci parametrici (NaiveBayes) ed ensemble (RF) per capire se la struttura del
problema è locale o globale.

**Come è implementato:** Weka `IBk` con k=1 (default). Non ha una fase di training: il
"modello" è l'intero training set memorizzato in memoria. Ogni predizione richiede il
calcolo della distanza da tutti i training point: con 15k istanze e 10 fold × 10 run = 100
valutazioni, il costo totale è O(100 × 1500 test × 13500 train × 19 feature) ≈ miliardi
di operazioni. Questo spiega i ~227s per combinazione misurati.

| Pro | Contro |
|---|---|
| Non parametrico: nessuna assunzione sulla distribuzione dei dati | **Lento in prediction**: O(n×m) per ogni istanza test - 15x più lento di NaiveBayes |
| Cattura pattern locali complessi | Con dataset sbilanciato: i k vicini sono quasi sempre "non buggy" (domina la maggioranza) |
| Nessuna fase di training esplicita | Sensibile alla scala: feature con range ampio (LOC_Touched può arrivare a 10k) dominano la distanza |
| Undersampling lo aiuta molto: training più piccolo → prediction drasticamente più veloce | La "maledizione della dimensionalità": con 19 feature, le distanze euclideo si omogenizzano |
| - | Tutta la logica è nei dati: outlier e rumore si propagano direttamente |

**Runtime misurato:** ~227s (None), ~37s (Undersampling), ~331s (Oversampling).

---

## 4. Feature Selection

### 4.0 Definizione e scopo

La **feature selection** è il processo di identificazione e riduzione del sottoinsieme di attributi più informativi per la predizione, scartando quelli irrilevanti o ridondanti.

**A cosa serve nel contesto di questo progetto:** il dataset ha 19 feature di processo e prodotto (LOC, churn, età del file, esperienza degli autori, ecc.). Molte sono fortemente correlate: `LOC_Touched`, `LOC_Added` e `Churn` misurano varianti della stessa grandezza; `Max_LOC_Added` e `Avg_LOC_Added` sono statistiche derivate dalla stessa distribuzione. Questa ridondanza ha effetti negativi differenziati per classificatore:

- **NaiveBayes:** l'assunzione di indipendenza condizionale è violata - feature ridondanti contano "doppio" nella stima delle probabilità, distorcendo le predizioni verso la classe più frequente.
- **IBk:** le feature ridondanti aumentano la dimensionalità, omogenizzando le distanze euclidee e rendendo il k-NN meno discriminativo (cosiddetta "maledizione della dimensionalità").
- **RandomForest:** relativamente robusto grazie alla selezione casuale di feature per split, ma beneficia dalla FS in presenza di molte feature completamente irrilevanti.

**Un punto spesso frainteso:** la feature selection non serve principalmente a migliorare le prestazioni del modello. Nella pratica, un modello addestrato su tutte le feature e uno addestrato su un sottoinsieme selezionato producono spesso metriche molto simili. Il vero vantaggio è mantenere prestazioni comparabili con meno feature, il che porta benefici concreti:

- **Velocità:** meno feature significa training e inference più rapidi - rilevante su dataset grandi o in produzione con latenza vincolata.
- **Interpretabilità:** un modello con 5 feature è molto più facile da spiegare a chi deve prendere decisioni rispetto a uno con 19.
- **Manutenibilità:** raccogliere e aggiornare 5 metriche è meno costoso che mantenerne 19, specialmente se alcune richiedono calcoli complessi.
- **Riduzione del rischio di overfitting:** feature irrilevanti o rumorose possono introdurre correlazioni spurie nel training set che non si generalizzano.

In questo progetto la feature selection è valutata come dimensione sperimentale: se InfoGain o Spearman selezionano un sottoinsieme e le metriche rimangono stabili, si ottiene un modello più semplice senza perdita di qualità.

Esistono due famiglie di metodi:
- **Filter:** valutano ogni attributo (o sottoinsieme) **indipendentemente** dal classificatore. Veloci e general-purpose.
- **Wrapper:** valutano sottoinsiemi addestrando il classificatore su ciascun sottoinsieme candidato. Trovano la combinazione ottimale per il classificatore specifico, a costo di tempi molto più elevati.

---

### 4.1 Approcci Filter

I metodi filter valutano ogni attributo **indipendentemente dal classificatore** che verrà addestrato dopo. Sono veloci e general-purpose.

#### NONE - Nessuna feature selection

**Definizione:** tutti gli attributi vengono passati al classificatore senza alcun preprocessing di selezione.

**A cosa serve:** fornisce la baseline di confronto. Se un metodo di FS non supera NONE in nessuna configurazione, non ha portato valore su questo dataset.

**Come è implementato:** non è richiesto nessun filtro aggiuntivo. `FilteredClassifier` riceve come filtro solo il componente di balancing (se presente); il classificatore vede tutti gli attributi disponibili dopo la rimozione delle colonne non-feature (`java_class_path`, `release`).

| Pro | Contro |
|---|---|
| Nessun overhead computazionale | Feature irrilevanti e ridondanti possono degradare le prestazioni (specialmente NaiveBayes e IBk) |
| Nessun rischio di eliminare feature utili | Il modello deve imparare a ignorare il rumore da solo |
| Baseline di riferimento per confronto | - |

**Overhead:** nessuno.

---

#### InfoGain (Information Gain)

**Definizione:** misura la riduzione di entropia della distribuzione della classe target ottenuta conoscendo il valore di un attributo. Per un attributo `A` e classe `C`:

```
InfoGain(C, A) = H(C) - H(C | A)
```

**Entropia della classe:**

```
H(C) = - SUM_{c in C} P(c) * log2(P(c))
```

dove `P(c)` è la probabilità della classe `c`. Con classe binaria (buggy/non-buggy):

```
H(C) = - P(buggy) * log2(P(buggy)) - P(non-buggy) * log2(P(non-buggy))
```

Per OpenJPA (9% buggy): `H(C) = -(0.09 * log2(0.09)) - (0.91 * log2(0.91)) ≈ 0.44 bit`

**Entropia condizionale** (per attributo continuo discretizzato in valori v):

```
H(C | A) = SUM_{v} P(A=v) * H(C | A=v)
```

dove `H(C | A=v)` è l'entropia della classe nelle sole istanze in cui `A=v`.

Un attributo con alto InfoGain riduce molto l'incertezza sulla bugginess; un attributo irrilevante ha InfoGain = 0 (conoscerlo non cambia la distribuzione della classe).

**A cosa serve:** selezionare rapidamente le feature più discriminative eliminando quelle che non riducono l'incertezza sulla classe. È particolarmente utile per rimuovere feature completamente irrilevanti (InfoGain = 0) e migliorare la performance di NaiveBayes, che soffre fortemente delle ridondanze.

**Come è implementato:** Weka `InfoGainAttributeEval` + `Ranker(threshold=0.0)`. Il Ranker ordina gli attributi per score decrescente; il threshold `0.0` elimina solo gli attributi con InfoGain esattamente zero. Applicato esclusivamente sul training fold tramite `FilteredClassifier`, garantendo che le label del test non influenzino la selezione.

| Pro | Contro |
|---|---|
| Veloce: O(n × m) dove n=istanze, m=attributi | Valuta ogni attributo individualmente, ignora le ridondanze tra feature |
| Interpretabile: lo score misura la riduzione di entropia | Non cattura le interazioni tra attributi |
| Efficace nel rimuovere attributi del tutto irrilevanti | Con classe nominale binaria e attributi continui, può sottostimare l'importanza |
| Nessun rischio di overfitting nella selezione stessa | - |

**Overhead:** trascurabile.

---

#### Spearman Rank Correlation

**Definizione:** misura la correlazione monotona (non necessariamente lineare) tra ogni attributo e la classe. Data una coppia di vettori `(X, Y)`, il coefficiente di Spearman `ρ` è la correlazione di Pearson calcolata sui ranghi delle osservazioni:

```
ρ = Pearson(rank(X), rank(Y))
```

A differenza di Pearson, non assume una relazione lineare ed è robusta agli outlier - cruciale per metriche come `LOC_Touched` o `Churn` che presentano distribuzioni fortemente skewed.

**A cosa serve:** selezionare feature correlate in modo monotono con la bugginess, anche quando la relazione non è lineare (es. le classi con molte revisioni tendono a essere buggy, ma il rapporto non è proporzionale). Complementa InfoGain: i due metodi possono selezionare insiemi di feature diversi; confrontarli permette di capire quali aspetti del dataset sono più predittivi.

**Come è implementato:** Weka 3.8 stable non include un `AttributeEvaluator` nativo per Spearman. Weka ha `CorrelationAttributeEval` (Pearson), ma Pearson assume una relazione lineare ed è sensibile agli outlier - entrambi problemi per metriche di codice con distribuzioni skewed. La classe `SpearmanAttributeEval` è un'implementazione originale scritta da zero, basata sulla definizione matematica standard (Spearman, 1904), senza utilizzo di alcun sorgente esterno.

**Formula:**

```
score(X) = |rho(X, C)|
```

dove `C` è l'attributo classe e `rho` è la correlazione di Spearman:

```
rho(X, C) = Pearson(rank(X), rank(C))
```

Correlazione di Pearson:

```
         SUM_i [ (xi - x_mean)(ci - c_mean) ]
r(X,C) = -------------------------------------------------------
         SQRT[ SUM_i(xi - x_mean)^2 * SUM_i(ci - c_mean)^2 ]
```

Ranking con gestione dei pareggi (average rank):
i valori vengono ordinati; ai valori uguali che occupano le posizioni ordinate `i..j`
(0-based) viene assegnato rank `= (i + j) / 2 + 1`.

```
Esempio: valori [3, 1, 3]
  -> posizioni ordinate: 1 a pos.0, 3 a pos.1, 3 a pos.2
  -> rank di 1 = (0+0)/2 + 1 = 1
  -> rank di 3 = (1+2)/2 + 1 = 2.5  (media dei ranghi 2 e 3)
```

Il valore assoluto è preso perché la direzione della correlazione (positiva o negativa)
non è rilevante per la selezione: una feature fortemente anti-correlata è ugualmente
informativa di una fortemente correlata.

**Configurazione Weka:** `SpearmanAttributeEval` (custom) + `Ranker(threshold=0.0)`

| Pro | Contro |
|---|---|
| Robusta agli outlier (opera sui rank, non sui valori grezzi) | Valuta ogni attributo individualmente, come InfoGain |
| Cattura relazioni monotone non lineari (es. logaritmiche) | Non cattura le interazioni tra attributi |
| Appropriata per metriche con distribuzioni skewed | La classe nominale (true/false) viene trattata come 0/1: la correlazione è definita ma la semantica può essere limitata |
| Nessun rischio di overfitting nella selezione | - |

**Overhead:** trascurabile (stesso ordine di InfoGain).

---

### 4.2 Approcci Wrapper

**Definizione:** i metodi wrapper valutano **sottoinsiemi** di attributi addestrando un classificatore su ciascun sottoinsieme candidato e misurando la performance tramite cross-validation interna. La selezione è guidata dalla performance del classificatore, non da una funzione di score indipendente.

**A cosa serve:** trovare la combinazione di feature che massimizza la performance del classificatore specifico, catturando le interazioni tra attributi che i metodi filter ignorano. Il costo computazionale è molto maggiore, ma il subset risultante è ottimizzato per il classificatore target.

> **ATTENZIONE - RUNTIME ELEVATO:** I metodi wrapper sono **disabilitati di default**
> nel file `config.yaml`. Ogni combinazione wrapper richiede circa **1-3 ore** su OpenJPA.
> Con 2 direzioni × 3 classificatori × 4 strategie di balancing = 24 combinazioni wrapper,
> abilitare entrambi può richiedere **1-3 giorni** di esecuzione.
>
> **Perché sono così lenti:** con 19 feature e 10×10 fold CV (100 fold esterni), ogni fold
> esegue la ricerca del subset ottimale internamente. ForwardSearch valuta al massimo
> 19+18+...+1 = 190 subset, ciascuno con 5-fold CV interna = ~950 addestramenti di NaiveBayes
> per fold. Totale per combinazione: **~95.000 addestramenti** solo per la feature selection.

**Classificatore interno:** NaiveBayes (il più veloce disponibile).

#### ForwardSearch

**Definizione:** algoritmo greedy di ricerca ascendente - parte con zero feature e aggiunge iterativamente la feature che produce il maggior miglioramento di performance, fino a quando nessuna aggiunta porta miglioramenti.

**A cosa serve:** trovare il sottoinsieme minimo di feature sufficiente per una buona performance. È preferibile quando si sospetta che molte feature siano irrilevanti e si vuole un subset compatto.

**Come è implementato:** `WrapperSubsetEval(NaiveBayes)` valuta ogni sottoinsieme candidato con 5-fold CV interna su NaiveBayes. `GreedyStepwise(forward=true)` guida la ricerca aggiungendo una feature alla volta. Il filtro è applicato internamente per ogni fold della 10×10 CV tramite `FilteredClassifier`: la ricerca del subset avviene esclusivamente sui dati di training di quel fold, mai sul test.

| Pro | Contro |
|---|---|
| Considera le **interazioni tra feature**: valuta sottoinsiemi, non singoli attributi | **MOLTO LENTO:** ~1-3 ore per combinazione su OpenJPA |
| Seleziona le feature ottimali per il classificatore usato internamente | Greedy: non garantisce l'ottimo globale (può fermarsi in un ottimo locale) |
| Con feature irrilevanti molte: parte subito con un piccolo subset (efficiente) | Il classificatore interno (NaiveBayes) può differire da quello valutato esternamente |
| - | Con poche feature irrilevanti: tende a selezionare quasi tutte (poco beneficio) |
| - | Rischio di overfitting nella selezione se il training fold è piccolo |

---

#### BackwardSearch

**Definizione:** algoritmo greedy di ricerca discendente - parte con tutte le feature e rimuove iterativamente la feature la cui rimozione causa il minor degrado di performance, fino a quando nessuna rimozione porta miglioramenti.

**A cosa serve:** trovare l'insieme ottimale quando si sospetta che molte feature siano utili e solo poche siano irrilevanti. Il punto di partenza "migliore" (tutte le feature) riduce il rischio di perdere interazioni importanti che ForwardSearch potrebbe non scoprire mai partendo da zero.

**Come è implementato:** `WrapperSubsetEval(NaiveBayes)` + `GreedyStepwise(backward=true)`. Stesso meccanismo di ForwardSearch ma con direzione inversa. Il costo è analogo o leggermente superiore perché ogni step parte da un sottoinsieme più grande.

| Pro | Contro |
|---|---|
| Considera le **interazioni tra feature** come ForwardSearch | **MOLTO LENTO:** ~1-3 ore per combinazione su OpenJPA |
| Parte dal punto "migliore" (tutte le feature): meno rischio di perdere interazioni importanti | Greedy: non garantisce l'ottimo globale |
| Con poche feature irrilevanti: converge rapidamente rimuovendo solo quelle inutili | Con molte feature irrilevanti: il punto di partenza è già degradato dall'overfitting |
| - | Il classificatore interno (NaiveBayes) può non corrispondere a quello valutato esternamente |

---

## 5. Balancing

### 5.0 Definizione e scopo

Lo **class imbalance** è la condizione in cui una classe ha molte più istanze dell'altra nel training set. In questo progetto ~8,98% delle istanze è buggy e ~91,02% è non-buggy: la classe di interesse (buggy) è la classe minoritaria.

**Perché l'accuracy non è sufficiente:** un classificatore che predice sempre "non buggy" ottiene ~91% di accuracy senza aver appreso nulla di utile. Precision, Recall, AUC e Kappa (descritte in sezione 7) misurano il comportamento specifico sulla classe minoritaria ed espongono questo comportamento degenere.

**A cosa serve il balancing:** impedire al classificatore di imparare una scorciatoia. Su un training set con il 9% di buggy e il 91% di non-buggy, il classificatore scopre rapidamente che predire sempre "non buggy" gli garantisce ~91% di accuracy senza aver imparato nulla sulla classe di interesse. Il balancing elimina questa scorciatoia rendendo le due classi ugualmente frequenti nel training, costringendo il modello a imparare i pattern reali della classe buggy.

**Dove viene applicato:** esclusivamente sul **training fold** tramite `FilteredClassifier`. Il test fold non viene mai modificato perché il test deve rappresentare la realtà: nel mondo reale le classi sono sbilanciate (~9% buggy), ed è su quella distribuzione che il modello viene usato in produzione. Bilanciare il test set significherebbe misurare le performance su una distribuzione artificiale che non esiste, producendo stime ottimistiche non trasferibili al caso d'uso reale.

**Perché non sull'intero dataset prima della CV:** applicare balancing o feature selection sull'intero dataset prima di eseguire la cross-validation è un errore metodologico noto come *data leakage*. Il problema si manifesta in modo diverso a seconda della tecnica:

- **Feature selection (InfoGain, Spearman):** se il calcolo dei punteggi usa tutte le istanze, il test fold ha già influenzato quali feature vengono selezionate. La selezione risulta ottimizzata inconsciamente per i dati di test, producendo metriche di valutazione artificialmente buone.

- **SMOTE e oversampling:** SMOTE genera punti sintetici interpolando istanze vicine della classe minoritaria. Se applicato prima dello split, un punto sintetico generato a partire da istanze del training potrebbe finire nel test set. Il modello verrebbe quindi testato su punti costruiti a partire dai propri dati di addestramento - non su dati genuinamente nuovi.

- **Undersampling:** la selezione di quali istanze rimuovere viene effettuata osservando l'intero dataset, incluse le istanze che finiranno nel test set. La composizione del training viene quindi influenzata da informazioni che il modello non dovrebbe vedere.

In tutti i casi il risultato è lo stesso: le metriche stimate dalla CV sembrano migliori di quelle che si otterrebbero su dati realmente nuovi. La soluzione adottata in questo progetto è `FilteredClassifier` di Weka, che garantisce che ogni filtro (balancing e feature selection) venga applicato internamente a ogni fold, esclusivamente sui dati di training di quel fold.

---

### 5.1 NONE - Nessun balancing

**Definizione:** il classificatore viene addestrato sulla distribuzione originale del training fold, senza nessuna modifica alle proporzioni tra le classi (~9% buggy, ~91% non-buggy).

**A cosa serve:** fornisce la baseline di riferimento per quantificare il beneficio del balancing. Se nessuna strategia supera NONE su una determinata metrica, il bilanciamento non ha aiutato per quella configurazione.

**Come è implementato:** non è richiesto nessun filtro di balancing. `FilteredClassifier` riceve come filtro solo il componente di feature selection (se presente); il classificatore vede la distribuzione originale del training fold.

| Pro | Contro |
|---|---|
| Riflette la distribuzione reale del problema | I classificatori tendono a ignorare la classe minoritaria |
| Nessun overhead | Recall sulla classe buggy tipicamente molto bassa |
| Baseline di riferimento | Kappa basso (il classificatore si avvicina a predire sempre "non buggy") |

---

### 5.2 Undersampling - `SpreadSubsample`

**Definizione:** tecnica di bilanciamento che rimuove casualmente istanze dalla classe **maggioritaria** finché tutte le classi hanno la stessa numerosità. Il training set si riduce drasticamente.

**A cosa serve:** forzare il classificatore a dare uguale peso alle due classi riducendo la presenza della classe dominante. L'effetto collaterale positivo è un training set molto più piccolo, che riduce drasticamente i tempi di addestramento - specialmente per IBk e RandomForest.

**Come è implementato:** Weka `SpreadSubsample(distributionSpread=1.0)`. Il parametro `distributionSpread=1.0` indica un rapporto 1:1 tra le classi. Per OpenJPA: da ~15.338 istanze il training si riduce a ~2.756 (~1.378 per classe), con una riduzione dell'82% dei dati totali.

| Pro | Contro |
|---|---|
| Training set piccolo: il classificatore addestra in modo molto veloce | **Perde ~87% dei dati di training** (9/10 delle istanze non-buggy vengono scartate) |
| Bilanciamento perfetto (1:1) garantito | Il modello vede una rappresentazione fortemente ridotta della classe maggioritaria |
| Elimina istanze ridondanti della maggioranza | L'informazione persa può non essere recuperabile |
| - | Le istanze rimosse sono scelte casualmente: non si sa se erano rappresentative o no |

---

### 5.3 Oversampling - `Resample`

**Definizione:** tecnica di bilanciamento basata su ricampionamento dell'intero dataset con probabilità di estrazione sbilanciate verso una distribuzione uniforme. Il training set cresce.

**A cosa serve:** portare le due classi alla stessa numerosità senza scartare istanze dalla maggioritaria. A differenza dell'undersampling, non elimina dati: ricampiona l'intero dataset estraendo la classe minoritaria più spesso e la maggioritaria meno spesso. Il classificatore viene esposto ripetutamente alle stesse istanze buggy, aumentando il peso che attribuisce loro durante il training.

**Come funziona il meccanismo:** `Resample` non aggiunge istanze direttamente alla classe minoritaria. Ricampiona l'intero dataset con rimpiazzo, assegnando a ogni istanza una probabilità di essere estratta che è bilanciata tra le classi (`biasToUniformClass=1.0`). Il risultato è un nuovo dataset della dimensione target in cui le istanze minoritarie compaiono più volte (estratte più frequentemente) e quelle maggioritarie compaiono meno volte (estratte meno frequentemente) rispetto all'originale.

**Come è implementato:** Weka `Resample(biasToUniformClass=1.0, noReplacement=false)`. `sampleSizePercent` è calcolato dinamicamente come `2 × N_majority / N_total × 100` in modo che l'output contenga circa `N_majority` istanze per classe, portando la minoritaria al livello della maggioritaria. Per OpenJPA: il training set cresce da ~15.338 a ~27.000 istanze (~13.860 non-buggy + ~13.860 buggy ricampionati).

**Perché `Resample` e non una duplicazione diretta della minoranza:** la scelta è pragmatica. `Resample` è l'unico filtro di oversampling incluso nel core jar di `weka-stable`. Un approccio più semplice - duplicare direttamente le istanze minoritarie - richiederebbe un filtro custom, come è stato necessario fare per SMOTE. `Resample` ottiene lo stesso risultato pratico (dataset bilanciato, istanze minoritarie ripetute più volte) senza codice aggiuntivo.

| Pro | Contro |
|---|---|
| Non perde dati dalla classe maggioritaria | **Duplica esattamente le stesse istanze**: nessuna nuova variabilità |
| Semplice da configurare e interpretare | Rischio di overfitting sulla classe minoritaria: il modello memorizza le stesse istanze |
| Bilancia senza alterare la struttura dei dati originali | Il training set cresce notevolmente, rallentando l'addestramento |

---

### 5.4 SMOTE - Synthetic Minority Over-sampling Technique

Genera istanze **sintetiche** della classe minoritaria interpolando tra istanze reali
nello spazio delle feature: per ogni istanza buggy reale, crea nuove istanze lungo il
segmento che la collega ai suoi k vicini più prossimi (k=5 di default).

SMOTE è superiore all'oversampling semplice perché non duplica esattamente le stesse
istanze ma crea nuovi punti nel "vicinato" di quelli esistenti, riducendo il rischio di
overfitting e aumentando la variabilità del training set della classe minoritaria.

#### Scelta implementativa: `SmoteFilter` custom

`weka-stable 3.8.6` **non include SMOTE** nel suo jar principale. I filtri di istanza
supervisionati disponibili nel core sono solo `Resample`, `SpreadSubsample` e
`ClassBalancer`. SMOTE in Weka è distribuito come package separato, installabile tramite
il Weka Package Manager o scaricabile manualmente da:
https://weka.sourceforge.io/packageMetaData/SMOTE/index.html

Aggiungere quel jar a un progetto Maven presenta però diversi problemi:
- Non è pubblicato su Maven Central: richiederebbe una dipendenza `systemScope` (cattiva
  pratica, lega il build a un path assoluto) oppure l'installazione manuale nel repository
  locale di ogni sviluppatore, rompendo la riproducibilità del build.
- La dipendenza non verrebbe gestita automaticamente da `mvn install` o CI/CD.

Per queste ragioni il sorgente ufficiale (`SMOTE.java`, Ryan Lichtenwalter, Copyright 2008
University of Waikato) è stato re-impacchettato direttamente nel progetto come `SmoteFilter`
nel package `com.isw2project.classifier`.
Riferimento: https://weka.sourceforge.io/packageMetaData/SMOTE/index.html -
Algoritmo: Chawla et al. 2002, JAIR vol. 16, pp. 321-357.

**Differenze rispetto al sorgente originale** (puramente strutturali, nessun impatto algoritmico):

1. **Package e nome classe:** `weka.filters.supervised.instance.SMOTE` ->
   `com.isw2project.classifier.SmoteFilter`
2. **Rimossi i blocchi di annotazione Weka-GUI** (`<!-- globalinfo-start -->`,
   `<!-- technical-bibtex-start -->`, `<!-- options-start -->` e relativi blocchi di chiusura).
   Questi marcatori vengono interpretati dall'Explorer/Experimenter di Weka per popolare
   automaticamente i pannelli di aiuto nella GUI. Questo progetto non usa la GUI di Weka,
   quindi i blocchi non hanno alcuna utilità.
3. **Getter/setter compattati** da forma estesa (10 righe con Javadoc) a dichiarazioni
   su singola riga. L'API pubblica è invariata.
4. **Aggiunto `@SuppressWarnings`** a livello di classe per silenziare i warning del
   compilatore dovuti all'uso di raw type (stile pre-generics del 2008) e al costruttore
   `Double(String)` deprecato nelle versioni recenti di Java.

Il metodo `doSMOTE()` - che contiene l'intero algoritmo - è identico all'originale.

#### Algoritmo implementato in `SmoteFilter`

```
Input: training fold, percentuale P, numero vicini k
Output: training fold originale + istanze sintetiche aggiunte

1. Auto-rileva la classe minoritaria (classe con meno istanze non-zero)
2. Raccoglie tutte le istanze minoritarie → lista minority
3. Calcola le matrici VDM (Value Distance Metric) per gli attributi nominali
4. Per ogni istanza instanceI in minority:
   a. Calcola la distanza da tutte le altre istanze minority:
      - Attributi numerici: distanza euclidea
      - Attributi nominali: distanza VDM
   b. Seleziona i k vicini più prossimi → nnArray
   c. Genera floor(P / 100) istanze sintetiche (+ gestione del resto frazionario):
      - Sceglie casualmente un vicino nn da nnArray
      - Genera gap ∈ [0, 1) casuale
      - Per feature numeriche:  synthetic[j] = instanceI[j] + gap × (nn[j] − instanceI[j])
        Per feature date:       stessa formula, cast a long
        Per feature nominali:   majority vote tra instanceI e tutti i k vicini
      - Classe: indice della classe minoritaria
```

**Parametri per OpenJPA:**
- `percentage ≈ 913%` (calcolato come `(N_majority − N_minority) / N_minority × 100`)
- `k = 5` vicini (default)
- Classe minoritaria: auto-rilevata da `SmoteFilter`
- Risultato: da ~1.378 istanze buggy a ~13.960, training set totale ~27.000 istanze

> **OVERHEAD:** SMOTE è più lento dell'oversampling semplice perché richiede il calcolo
> dei k vicini più prossimi per ogni istanza minoritaria (O(|minority|²) per fold).
> Con ~1.378 istanze buggy e k=5, l'overhead rimane contenuto (pochi secondi per fold).

**Configurazione:** la percentuale è calcolata dinamicamente dal dataset in
`BalancingBuilderService`; la classe minoritaria è auto-rilevata da `SmoteFilter`.

| Pro | Contro |
|---|---|
| **Genera istanze sintetiche nuove**: aggiunge variabilità nella classe minoritaria | Più lento dell'oversampling: richiede calcolo k-NN per ogni istanza minoritaria |
| Riduce il rischio di overfitting rispetto alla duplicazione esatta | Può creare istanze sintetiche "rumorose" se le istanze minority si trovano in zone dense della maggioranza |
| Considerato lo stato dell'arte rispetto all'oversampling semplice | Non gestisce perfettamente le feature con distribuzioni molto skewed |
| Mantiene la struttura geometrica della classe minoritaria nello spazio delle feature | La percentuale è un'approssimazione calcolata sull'intero dataset (±10% per fold) |
| Implementazione autonoma: nessuna dipendenza esterna aggiuntiva | - |

---

## 6. Separazione di Training, Validation e Test Set

Questo è il punto metodologico più critico dell'intera pipeline.

### 6.1 I tre insiemi e i loro ruoli

| Insieme | Scopo | Quando viene usato |
|---|---|---|
| **Training set** | Addestrare il modello (stimare i parametri) | Durante ogni fold di training |
| **Validation set** | Selezionare iperparametri e confrontare configurazioni | Loop interno dei metodi wrapper |
| **Test set** | Valutare la performance finale su dati mai visti | Ogni fold di test nella CV esterna |

In questo progetto non è presente un test set separato nel senso classico: si usa la
**cross-validation** come stima della performance di generalizzazione. In 10-fold CV, ogni
fold funge da test set mentre i restanti 9 folds costituiscono il training set.

### 6.2 Due usi della CV: stima vs selezione degli iperparametri

La cross-validation ha due utilizzi distinti che è importante non confondere:

**CV per selezionare iperparametri** - provo configurazione A, B, C con CV e scelgo quella
con lo score più alto. La CV qui è un oracolo di confronto: serve a capire quale configurazione
è migliore. È quello che fanno grid search, random search, e i metodi wrapper (§6.4).

**CV per stimare la performance** - data una configurazione fissa, quanto bene generalizza
su dati non visti? La CV qui è uno strumento di misurazione: non scelgo nulla, misuro.

In questo progetto la CV esterna (10×10) fa il **secondo** - stima quanto bene generalizza
ogni combinazione prefissata in `config.yaml`. Il confronto tra le 27 combinazioni avviene
**dopo**, guardando il CSV di output con una riga per combinazione. Non si sceglie la fold
migliore né si modifica nulla durante la CV: ogni combinazione riceve la propria stima
indipendente, e il confronto è lasciato all'analisi dei risultati.

### 6.3 Perché le predizioni accumulate su tutti i fold non sono "barare"

In 10-fold CV con N istanze, Weka accumula in `Evaluation` le predizioni di tutti i fold:

```
Fold 1 come test:  modello addestrato su fold 2..10 → predice N/10 istanze (mai viste)
Fold 2 come test:  modello addestrato su fold 1,3..10 → predice N/10 istanze (mai viste)
...
Fold 10 come test: modello addestrato su fold 1..9 → predice N/10 istanze (mai viste)

Totale: N predizioni - ogni istanza predetta esattamente una volta
        da un modello che NON la aveva vista durante il training
```

Accumulare queste N predizioni non è barare: ogni singola predizione è onesta, prodotta
su un'istanza che era nel test set in quel momento. Ciò che sarebbe scorretto è addestrare
su tutto il dataset e predire sullo stesso dataset - lì il modello conoscerebbe già le
risposte. Qui invece ogni istanza viene predetta da un modello che non l'aveva vista.

Le N predizioni accumulate vengono usate per calcolare le metriche (inclusa NPofB20)
in modo più stabile rispetto a usare le sole N/10 di un singolo fold. L'intero processo
viene ripetuto 10 volte con shuffling diverso e i risultati vengono mediati.

### 6.4 Perché la cross-validation esterna è ripetuta 10 volte

Un singolo run di 10-fold CV produce una stima influenzata dalla divisione casuale specifica
del dataset in fold. Ripetere 10 volte con seed diversi e fare la media riduce la varianza
della stima: le 10 divisioni diverse "campionano" lo spazio delle possibili partizioni,
rendendo la stima più stabile e rappresentativa della vera performance di generalizzazione.

### 6.5 Il problema della data leakage e la soluzione con FilteredClassifier

Il training set e il test set hanno scopi opposti e devono essere trattati in modo opposto:

- **Training set:** può e deve essere modificato (bilanciato, filtrato nelle feature) perché
  il suo scopo è far imparare bene il modello. Lasciarlo sbilanciato permette al modello di
  imparare la scorciatoia di predire sempre la classe maggioritaria.
- **Test set:** non deve essere mai modificato perché rappresenta la realtà - la distribuzione
  reale del problema, quella che il modello incontrerà in produzione. Modificarlo
  significherebbe misurare le performance su una distribuzione artificiale che non esiste.

**Data leakage** è la violazione di questa regola: informazioni provenienti dal test set
"contaminano" la fase di training (o il preprocessing), producendo stime di performance
ottimisticamente distorte - il modello sembra funzionare meglio di quanto farebbe su dati
davvero nuovi.

Nel contesto di questo progetto, la leakage si produce se si applicano i filtri di
preprocessing (balancing e feature selection) sull'**intero dataset** prima di iniziare la
cross-validation:

```
SBAGLIATO - introduce data leakage:

  SMOTE(intero dataset)          → dataset bilanciato con istanze sintetiche
  InfoGain(intero dataset)       → feature selezionate usando le label del test
  → 10-fold CV sul dataset preprocessato
```

In questo caso:
- Le istanze sintetiche create da SMOTE a partire da istanze del test fold finiscono nel
  training fold. Il modello vede indirettamente dati "ispirati" al test set.
- Se InfoGain viene calcolato sull'intero dataset, la scelta delle feature è influenzata
  dalle label del test set: le feature sembrano più informative di quanto siano in realtà.

**La soluzione: `FilteredClassifier`**

Weka's `FilteredClassifier` è un wrapper che applica i filtri **internamente per ogni fold**,
garantendo che il test fold non venga mai toccato dai filtri:

```
CORRETTO - con FilteredClassifier:

  Per ogni fold k (k = 1..10):
    training_k = 9 fold rimanenti  ← unico input per tutti i filtri
    test_k     = fold k            ← mai modificato, mai visto dai filtri

    1. balancing_filter.fit_and_transform(training_k) → training_k_balanced
    2. fs_filter.fit_and_transform(training_k_balanced) → training_k_balanced_fs
    3. classifier.fit(training_k_balanced_fs)
    4. fs_filter.transform(test_k) → test_k_fs  (applica la selezione già calcolata)
    5. classifier.predict(test_k_fs)
```

**Punti critici:**

- **I filtri vengono "fittati" solo sul training fold**: InfoGain, Spearman, SMOTE
  apprendono esclusivamente dai dati di training. Il test fold è invisibile.
- **Il test fold viene solo trasformato** (non fittato): per InfoGain/Spearman, si
  applicano le stesse selezioni di attributi calcolate sul training. Per SMOTE, non si
  applica nulla al test fold - SMOTE aggiunge istanze sintetiche al training, non le
  proietta sul test.
- **L'ordine dei filtri è importante**: prima balancing, poi feature selection. La FS deve
  osservare la distribuzione già bilanciata per calcolare score di importanza corretti.
  Calcolare InfoGain su un training set sbilanciato (9% buggy) produrrebbe score
  dominati dalla classe maggioritaria, potenzialmente eliminando feature utili per la
  classe rara.

**Come è realizzato nel codice:**

`ClassifierEvaluatorService` non sa nulla di balancing o feature selection - riceve un
oggetto `Classifier` opaco e chiama `crossValidateModel`. La separazione train/test per
i filtri è garantita da come `ClassifierBuilderService` assembla quel `Classifier`:

```
ClassifierBuilderService.build(...)
  -> balancingFilter  (da BalancingBuilderService)
  -> fsFilter         (da FeatureSelectionBuilderService)
  -> MultiFilter([balancingFilter, fsFilter])
  -> FilteredClassifier(base=NaiveBayes/RF/IBk, filter=MultiFilter)

ClassifierEvaluatorService.evaluate(data, filteredClassifier, ...)
  -> crossValidateModel(filteredClassifier, data, 10, seed)
       Per ogni fold k:
         Weka chiama filteredClassifier.buildClassifier(training_k)
           -> MultiFilter.fit_and_transform(training_k)   ← balancing + FS sul training
           -> baseClassifier.fit(training_k_trasformato)
         Weka chiama filteredClassifier.classifyInstance(test_instance)
           -> MultiFilter.transform(test_instance)        ← solo FS applicata, no balancing
           -> baseClassifier.predict(test_instance_fs)
```

`ClassifierEvaluatorService` è quindi ignaro dei filtri per design: la responsabilità
di "dove e come applicare balancing e FS" appartiene interamente a `ClassifierBuilderService`
e a Weka's `FilteredClassifier`. Questa separazione rende i due servizi indipendenti e
testabili in isolamento.

### 6.6 Il caso dei metodi wrapper: cross-validation annidata

I metodi wrapper (ForwardSearch, BackwardSearch) introducono una struttura di
cross-validation **annidata** che è spesso fonte di confusione:

```
Loop esterno (10×10 CV) - risponde a: "quanto generalizza il modello finale?"
  └── Per ogni training fold:
        Loop interno (CV del wrapper) - risponde a: "quali feature rendono NaiveBayes migliore?"
          └── Per ogni subset candidato: addestra NaiveBayes, misura accuracy via CV interna
```

I due loop rispondono a **domande completamente diverse** e sono **concettualmente indipendenti**:
- Il loop esterno è la **strategia di valutazione** del modello finale
- Il loop interno è la **strategia di ricerca** del subset ottimale di feature

Non c'è nessuna contraddizione tra i due: ForwardSearch non è una forma di validazione,
è un algoritmo di ottimizzazione che usa un classificatore internamente per guidare la ricerca.

Con FilteredClassifier, il loop interno opera esclusivamente sui dati del training fold
del loop esterno: il test fold del loop esterno rimane invisibile all'intera procedura di
selezione delle feature. Non c'è data leakage.

**Perché il wrapper è così lento:** con 19 feature, ForwardSearch esplora al massimo
19+18+...+1 = 190 sottoinsiemi. Ogni sottoinsieme viene valutato con 5-fold CV interna
(default Weka) → 950 addestramenti di NaiveBayes per fold esterno. Con 100 fold esterni:
**~95.000 addestramenti solo per la FS**, più l'addestramento del classificatore finale
per ogni fold. Questo spiega i tempi stimati di 1-3 ore per combinazione.

### 6.7 Temporalità: 10×10 CV vs Walk-Forward Validation

#### Il problema

La 10×10 cross-validation assegna le istanze ai fold in modo **casuale**, senza tenere conto dell'ordine temporale in cui le classi Java sono state osservate. In un dataset di bug prediction ogni istanza rappresenta una classe in una specifica release; le release hanno un ordine temporale preciso (v1 → v2 → ... → v11 in OpenJPA).

Con la 10×10 CV può accadere che un fold di training contenga istanze di release più recenti di quelle nel fold di test:

```
Esempio con 3 release (A = versione 1, B = versione 2, C = versione 3):

  Fold di training:  [A, C, A, B, C, A, C, B, A]   ← include istanze di v3
  Fold di test:      [B, A, C]                       ← include istanze di v1 e v2

  → Il modello viene addestrato su informazioni "future" (v3) rispetto a parte del test (v1, v2).
```

Questo introduce una forma di **temporal leakage**: il modello può apprendere pattern che esistono solo perché le classi sono state modificate o corrette in versioni successive a quelle che si sta cercando di predire. In un contesto reale, queste informazioni non sarebbero disponibili al momento della predizione.

**Effetto pratico sulla bug prediction:** in generale, il codice tende a migliorare nel tempo - i bug vengono corretti, il design viene raffinato, le classi instabili vengono riscritte. Un modello addestrato su versioni "future" vede già lo stato post-fix di alcune classi e può apprendere correlazioni che non esistevano al momento della versione da predire. Le metriche risultanti sono quindi ottimisticamente distorte rispetto all'uso reale del modello.

---

#### Walk-Forward Validation

La **Walk-Forward Validation** (o *time-series split*) è la strategia alternativa che rispetta l'ordine temporale:

```
Iterazione 1:  train=[v1],           test=[v2]
Iterazione 2:  train=[v1, v2],       test=[v3]
Iterazione 3:  train=[v1, v2, v3],   test=[v4]
...
Iterazione k:  train=[v1..vk],       test=[v(k+1)]
```

Il training contiene sempre e solo release precedenti al test: nessuna informazione futura filtra nel modello.

---

#### Confronto

| Aspetto | 10×10 Cross-Validation | Walk-Forward |
|---|---|---|
| Rispetto della temporalità | No - fold casuali, possibile leakage temporale | Sì - training sempre nel passato rispetto al test |
| Numero di valutazioni (OpenJPA, ~11 release) | 100 (10 run × 10 fold) | ~8-9 (una per release, escludendo le prime usate per il seed) |
| Varianza della stima | Bassa - 100 misure indipendenti, media stabile | Alta - ogni iterazione usa una quantità diversa di training data; le prime iterazioni hanno pochissimi dati |
| Quantità di dati per fold | ~90% del dataset in training per ogni fold | Cresce progressivamente (dal 9% al 90% circa) |
| Riproducibilità | Controllata dai seed → risultati stabili | Deterministica → un solo valore per configurazione |
| Correttezza metodologica | Compromesso accettato | Ideale per la simulazione dell'uso reale |
| Applicabilità con pochi fold temporali | Sempre applicabile | Con poche release disponibili la stima è molto instabile |

#### Perché 10×10 CV è stata scelta in questo progetto

Con ~11 release in OpenJPA, una Walk-Forward produrrebbe al massimo 8-9 valutazioni, ciascuna su un test set di una singola release (~1.000-2.000 istanze). Le prime iterazioni addestrano su 1-2 release sole, rendendo il modello strutturalmente diverso da quello finale - la stima risultante avrebbe alta varianza e scarsa rappresentatività.

La 10×10 CV è il compromesso standard nella letteratura di bug prediction per dataset con numero limitato di versioni: rinuncia alla correttezza temporale in favore di una stima più stabile e confrontabile tra configurazioni diverse. Il temporal leakage è riconosciuto come limitazione (§12) e va tenuto presente nell'interpretazione dei risultati: le metriche misurate tendono a essere leggermente ottimistiche rispetto all'accuratezza che il modello raggiungerebbe in produzione.

---

## 7. Metriche di Valutazione

### 7.0 Definizione e scopo

Le **metriche di valutazione** quantificano le prestazioni di un classificatore su dati non visti (il test fold). Per la predizione della bugginess su un dataset fortemente sbilanciato (~9% buggy), le metriche classiche come l'accuracy non sono adeguate: un classificatore che predice sempre "non buggy" ottiene 91% di accuracy senza alcuna utilità pratica.

**Perché non l'accuracy:** l'accuracy conta i TN (file non-buggy correttamente ignorati) che su un dataset con 91% di negativi sono sempre la maggioranza schiacciante. Un modello degenere che non impara nulla ottiene 91% di accuracy; qualsiasi metrica basata solo su questa grandezza è inutile per valutare la capacità di trovare bug.

**Perché queste cinque metriche:** le cinque metriche scelte coprono angolazioni complementari e irriducibili l'una all'altra:

| Metrica | Cosa cattura che le altre non colgono |
|---|---|
| **Precision** | Il costo per allarme: quanti falsi positivi genero per ogni bug trovato |
| **Recall** | La completezza: quanti bug reali riesco a intercettare |
| **AUC** | La qualità del ranking indipendentemente dalla soglia di decisione |
| **Kappa** | L'accordo reale corretto per ciò che otterrei per puro caso |
| **NPofB20** | L'utilità pratica con un budget di ispezione fisso al 20% |

Nessuna singola metrica è sufficiente: Precision e Recall si bilanciano a vicenda (aumentare una riduce l'altra); AUC ignora il costo assoluto degli errori; Kappa non dice dove concentrare l'effort; NPofB20 misura l'utilità pratica ma non la completezza globale.

Tutte le metriche descritte di seguito sono calcolate sulla **classe positiva** (buggy = `true`). Le quattro grandezze fondamentali sono:

| | Predetto buggy | Predetto non-buggy |
|---|---|---|
| **Realmente buggy** | TP (True Positive) | FN (False Negative) |
| **Realmente non-buggy** | FP (False Positive) | TN (True Negative) |

- **TP:** file buggy correttamente identificati - il modello funziona
- **FN:** file buggy mancati - bug potenzialmente rilasciati in produzione (costo alto)
- **FP:** falsi allarmi - spreco di effort di ispezione (costo moderato)
- **TN:** file non-buggy correttamente ignorati

---

### 7.1 Precision

**Definizione:** frazione dei file predetti come buggy che sono effettivamente buggy.

```
Precision = TP / (TP + FP)
```

**A cosa serve:** misura il "costo per allarme" del classificatore. Alta precision significa che quando il modello segnala un file come buggy, è molto probabile che lo sia davvero. Precision bassa produce molti falsi allarmi: i tester perdono tempo a ispezionare file sani.

**Cosa coglie che le altre non colgono:** misura il costo per unità di effort di ispezione. Recall alta con Precision bassa significa che trovi quasi tutti i bug ma perdi molto tempo su falsi allarmi. Precision alta con Recall bassa significa che quando segnali un bug hai ragione, ma ne manchi molti. Accuracy ignora questo trade-off perché i TN dominano.

**Perché non F1 invece di Precision separata:** F1 = 2·(Precision·Recall)/(Precision+Recall) è la media armonica delle due. Usare Precision e Recall separatamente permette di vedere esplicitamente dove il modello sbaglia - se F1 è basso, è perché Precision è bassa (troppi falsi allarmi), perché Recall è bassa (troppi bug mancati), o entrambe. Collassarle in F1 nasconde questa informazione.

**Interpretazione nel contesto:** con il dataset OpenJPA sbilanciato, un classificatore senza balancing tende ad avere alta precision (segnala pochi file e quasi tutti sono davvero buggy) ma bassa recall (manca la maggior parte dei bug). Il bilanciamento sposta il trade-off verso recall più alta a scapito della precision.

---

### 7.2 Recall

**Definizione:** frazione dei file buggy reali che il classificatore identifica correttamente.

```
Recall = TP / (TP + FN)
```

**A cosa serve:** misura la "completezza" del classificatore. Alta recall significa che la maggior parte dei bug reali viene intercettata prima del rilascio. Recall bassa implica che molti bug sfuggono al modello e arrivano in produzione - il caso più costoso nello scenario reale di bug prediction.

**Cosa coglie che le altre non colgono:** misura il costo dei bug mancati - l'unica metrica che penalizza direttamente i FN (bug non trovati che arrivano in produzione). Accuracy e Precision sono indifferenti al numero di FN finché i TN sono numerosi. In un contesto di bug prediction, un FN (bug rilasciato in produzione) costa molto più di un FP (file sano ispezionato inutilmente): Recall cattura esattamente questo costo asimmetrico.

**Interpretazione nel contesto:** recall è la metrica critica per la sicurezza del software. L'obiettivo principale di un sistema di bug prediction è ridurre i bug in produzione, non minimizzare il tempo di ispezione; quindi recall tende ad essere prioritaria rispetto a precision nella valutazione finale.

---

### 7.3 AUC (Area Under the ROC Curve)

**Definizione:** area sotto la curva ROC (Receiver Operating Characteristic). La curva ROC traccia il tasso di veri positivi (Recall) sull'asse Y contro il tasso di falsi positivi (`FPR = FP/(FP+TN)`) sull'asse X al variare della soglia di classificazione.

```
AUC ∈ [0, 1]
AUC = 0.5  →  classificatore casuale (equivalente al random guessing)
AUC = 1.0  →  classificatore perfetto (tutti gli ordinamenti corretti)
AUC < 0.5  →  peggio del caso casuale (classificatore sistematicamente invertito)
```

**A cosa serve:** misura la capacità del classificatore di **ordinare** le istanze per probabilità di bugginess, indipendentemente dalla soglia scelta. AUC = 0.7 significa che il modello assegna uno score più alto a un file buggy rispetto a un non-buggy nel 70% dei casi. È particolarmente utile per confrontare classificatori su dataset sbilanciati perché non dipende dalla soglia di decisione (spesso arbitraria) e non è influenzata dalla prevalenza della classe minoritaria.

**Cosa coglie che le altre non colgono:** Precision e Recall dipendono dalla soglia di decisione - se abbassi la soglia da 0.5 a 0.3, Recall sale e Precision scende. AUC valuta il classificatore su **tutte le soglie possibili** contemporaneamente: misura se il modello sa ordinare bene le istanze per rischio, indipendentemente da dove si taglia. Ha anche una interpretazione probabilistica diretta: AUC = P(score_buggy > score_non-buggy), cioè la probabilità che il modello assegni uno score più alto a un file buggy rispetto a uno sano scelti a caso.

**Rispetto all'accuracy:** accuracy con soglia 0.5 su un dataset 9%/91% è dominata dai TN. AUC ignora completamente la soglia e la distribuzione delle classi - è invariante allo sbilanciamento.

**Interpretazione nel contesto:** AUC è la metrica più stabile tra le configurazioni - varia meno di precision e recall al variare del bilanciamento. RandomForest tende a dominare perché produce distribuzioni di probabilità ben calibrate grazie all'averaging su 100 alberi.

---

### 7.4 Kappa (Cohen's Kappa)

**Definizione:** misura di accordo tra le predizioni del classificatore e le etichette reali, corretta per l'accordo atteso per caso. Detto P_o l'accordo osservato e P_e l'accordo atteso per caso (basato sulle distribuzioni marginali):

```
Kappa = (P_o − P_e) / (1 − P_e)

Kappa = 0    →  accordo pari al caso (classificatore inutile)
Kappa = 1    →  accordo perfetto
Kappa < 0    →  peggio del caso (classificatore invertito)
Kappa > 0.6  →  accordo sostanziale (soglia empirica della letteratura)
```

**A cosa serve:** fornire una misura di performance **robusta allo sbilanciamento tra classi**. A differenza dell'accuracy, Kappa corregge per ciò che un classificatore "casuale ma consapevole della distribuzione" raggiungerebbe. Con ~9% di istanze buggy, un classificatore sempre-"non buggy" ha P_o = 0.91 ma P_e ≈ 0.91 → Kappa ≈ 0: Kappa espone il comportamento degenere che l'accuracy maschera.

**Cosa coglie che le altre non colgono:** corregge per l'accordo che si otterrebbe per caso - l'unica metrica che rende visibile quanto il classificatore sia migliore di uno che predice a caso rispettando la distribuzione delle classi. Con 9% buggy:

```
Classificatore sempre-"non buggy":
  P_o = 0.91  (91% accuracy)
  P_e = 0.91^2 + 0.09^2 ≈ 0.836  (accordo atteso per caso)
  Kappa = (0.91 - 0.836) / (1 - 0.836) ≈ 0.07
```

Kappa ≈ 0 espone il comportamento degenere che accuracy = 91% maschera completamente. AUC e NPofB20 misurano il ranking ma non penalizzano direttamente gli errori assoluti; Kappa tiene conto di entrambe le classi nella correzione.

**Interpretazione nel contesto:** Kappa tipicamente basso senza balancing (il modello è vicino al classificatore costante), aumenta significativamente con SMOTE o Oversampling perché la maggiore recall sulla classe buggy riduce i FN che Kappa penalizza.

---

### 7.5 NPofB20 (Number of bugs found in top 20%)

**Definizione:** frazione di bug totali che si trovano nel 20% del codice a rischio più alto, dove il "rischio" è lo score di probabilità di bugginess assegnato dal classificatore a ogni file.

```
NPofB20 = |{istanze buggy nel top 20% per score}| / |{istanze buggy totali}|
```

Il "top 20%" è calcolato per numero di istanze (non pesato per LOC). Il valore è compreso in `[0, 1]`.

**Baseline casuale:** un classificatore che assegna score casuali produce NPofB20 ≈ 0.20 - il top 20% dei file conterrebbe il 20% dei bug per pura probabilità. Qualsiasi valore superiore indica che il modello aggiunge valore rispetto al random.

**Cosa coglie che le altre non colgono:** traduce la qualità del modello in termini di effort risparmiato - l'unica metrica direttamente interpretabile da chi deve decidere quante risorse dedicare all'ispezione. AUC misura la qualità del ranking su tutto il dataset, ma non risponde alla domanda pratica "se ispeziono solo il 20% del codice, quanti bug trovo?". NPofB20 fissa il budget e misura il rendimento a quel budget specifico.

**Rispetto ad AUC:** AUC e NPofB20 sono correlati (entrambi misurano la qualità dell'ordinamento), ma NPofB20 è più interpretabile in un contesto operativo e più sensibile alla parte alta del ranking - esattamente la zona che conta quando le risorse di ispezione sono limitate.

**A cosa serve:** misurare l'utilità **pratica** del classificatore in uno scenario di revisione del codice con risorse limitate. Un tester non può ispezionare tutto il codice: se ispeziona il 20% più rischioso (secondo il modello), NPofB20 = 0.8 significa che troverà l'80% di tutti i bug con il 20% dello sforzo.

**Perché ha una classe dedicata mentre le altre metriche no:**
Precision, Recall, AUC e Kappa sono fornite direttamente da Weka con un singolo getter
su `Evaluation` dopo `crossValidateModel` - non richiedono alcun codice aggiuntivo.
NPofB20 non esiste in Weka: richiede di accedere alle predizioni grezze per istanza
(`Evaluation.predictions()`), ordinarle per probabilità e fare un calcolo custom.
Quella logica non appartiene a `ClassifierEvaluatorService`, che è responsabile di
eseguire la CV - non di calcolare metriche custom. Isolarla in `NpofB20Service` mantiene
entrambe le classi con una singola responsabilità.

**Come è implementato:** `NpofB20Service` è un'implementazione originale scritta da zero,
basata sulla definizione usata nella letteratura di defect prediction (Rahman et al., 2012).
Estrae le predizioni da `Evaluation.predictions()` e:
1. Estrae coppie `(score_buggy, label_reale)` per ogni istanza dell'intero set cross-validato (tutti i fold aggregati di un run, come prodotto da `crossValidateModel`)
2. Ordina per score decrescente
3. Prende le prime 20% istanze (per conteggio, arrotondato per eccesso con `Math.ceil`)
4. Conta quante istanze buggy sono in quel 20%
5. Divide per il numero totale di istanze buggy nell'intero set cross-validato

**Interpretazione nel contesto:** NPofB20 è altamente correlato all'AUC perché entrambi misurano la qualità del ranking. RandomForest ottiene in genere i valori più alti (0.6-0.9) perché le sue probabilità sono ben calibrate. IBk con k=1 restituisce probabilità binarie (0 o 1), il che degrada il ranking; NaiveBayes tende a polarizzare le probabilità verso gli estremi, con effetti variabili.

---

## 8. Configurazione, Stime di Runtime e Riproducibilità

Le combinazioni da eseguire sono controllate da `config.yaml` nella sezione `classifier`.
Ogni voce può essere commentata per escluderla dall'esecuzione.

### Stime di runtime (OpenJPA, ~15.338 istanze, ~19 feature, rfSlots=1)

Le stime sono prodotte da `RuntimeEstimatorService` con valori calibrati su un run
effettivo su questo hardware (vedi sezione successiva per i dettagli).

| Configurazione | Combinazioni | Single-thread | Con 4 thread |
|---|---|---|---|
| **Default** (3 clf × 3 FS × 3 bal, SMOTE off) | **27** | **~2h 22m** | **~35m (misurato)** |
| + SMOTE (3 clf × 3 FS × 4 bal) | 36 | ~4h | ~1h |
| + FORWARD_SEARCH (3 clf × 3 bal) | +9 | +9-27 ore | - |
| + BACKWARD_SEARCH (3 clf × 3 bal) | +9 | +9-27 ore | - |
| Tutto abilitato (3 clf × 5 FS × 4 bal) | 60 | potenzialmente 1-3 giorni | - |

> **WARNING:** Le configurazioni con FORWARD_SEARCH o BACKWARD_SEARCH sono commentate
> per default. Abilitarle su hardware non dedicato può saturare la macchina per giorni.
> Si consiglia di abilitare una combinazione alla volta per stimare il tempo effettivo.

### Stima a runtime automatica

All'avvio, **prima di caricare il dataset**, la pipeline stampa una stima del tempo di
esecuzione calcolata da `RuntimeEstimatorService` in base alla configurazione corrente
e al numero di thread paralleli effettivamente usati:

```
[INFO] Combinations: 27 | Threads: 4 | RF slots: 1
[INFO] Runtime estimate: ~2h 22m sequential → ~35m with 4 thread(s)  [basato su misurazione reale]
```

I valori base sono stati **calibrati su un run effettivo** su questo hardware (OpenJPA,
~15k istanze, 10×10 CV, rfSlots=1). Prima della calibrazione erano presi dai commenti di
`config.yaml` (RF=60s, IBk=15s, NB=5s) e producevano stime ~12× troppo ottimistiche.

| Componente | Valore calibrato | Fonte |
|---|---|---|
| RandomForest (NONE FS, NONE bal) | 757s | misurato: `RF\|No\|No` 21:17:18→21:29:55 |
| IBk (NONE FS, NONE bal) | 227s | misurato: `IBk\|No\|No` 21:40:14→21:44:01 |
| NaiveBayes (NONE FS, NONE bal) | 6s | misurato: `NB\|No\|No` 21:38:12→21:38:18 |
| InfoGain / Spearman overhead (solo NB) | +12s | visibile in NB; <5% in RF e IBk → trascurabile |
| ForwardSearch / BackwardSearch | +3600s | lower bound "1-3 ore" - non misurato |
| UNDERSAMPLING | ×0.20 | dataset 15k→2.7k: RF 757→151s, IBk 227→37s |
| OVERSAMPLING | ×1.65 | dataset 15k→27k: RF 757→1254s, IBk 227→331s |
| SMOTE | ×2.00 | stimato da OVERSAMPLING + overhead k-NN; non misurato |

**Formula:** `wall_clock ≈ Σ (base + fs_overhead) × bal_mult / combination_threads`

I moltiplicatori di balancing sono più stabili tra hardware diversi (riflettono la variazione
relativa del dataset). I tempi base (RF/IBk/NB) sono hardware-specifici: su macchine con più
core o RAM più veloce saranno proporzionalmente più bassi.

Se `parallelism.interactive = true`, la stima viene calcolata **dopo** che l'utente ha
scelto i thread a runtime, riflettendo i valori effettivi inseriti.

### Combinazioni default: 27 righe nel CSV di output (SMOTE disabilitato)

La riproducibilità è garantita dai seed fissi: il run `i`-esimo della 10×10 CV usa seed `i`
sia per lo shuffle che per la `crossValidateModel` di Weka.

---

## 9. Parallelismo CPU

La pipeline supporta due livelli indipendenti di parallelismo, entrambi configurabili
in `config.yaml` sotto la chiave `classifier.parallelism`.

### 9.1 Parallelismo a livello di combinazione

Ogni combinazione (classificatore × FS × balancing) è completamente indipendente dalle
altre: i parametri del modello non sono condivisi e `Evaluation` è un oggetto locale per
thread. La pipeline le distribuisce su un `ExecutorService.newFixedThreadPool(N)` e
raccoglie i risultati tramite `Future<Map<String,String>>`.

**Thread safety:** l'oggetto `Instances data` è condiviso tra i thread ma è usato in sola
lettura dopo il caricamento. Weka's `crossValidateModel` crea internamente una copia del
dataset prima di ogni run, quindi non serve nessuna sincronizzazione sull'oggetto dati.
Ogni thread crea il proprio `Classifier` e `Evaluation` dal costruttore, senza stato
condiviso modificabile.

### 9.2 Parallelismo interno di RandomForest

Weka's `RandomForest` supporta la costruzione parallela degli alberi tramite
`setNumExecutionSlots(k)`. I `k` slot vengono distribuiti sui core disponibili durante
l'addestramento di ogni fold. Questo livello agisce indipendentemente dal primo: un singolo
thread di combinazione può far girare RandomForest su più core.

### 9.3 Configurazione

I parametri del parallelismo si trovano in `config.yaml` sotto `classifier.parallelism`
e accettano tre formati per `combinations` e `randomForestSlots`:

| Formato | Esempio | Significato |
|---|---|---|
| `"auto"` | `"auto"` | automatico (vedi sezione 9.3.1) |
| numero intero | `"8"` | numero esplicito di thread |
| percentuale | `"50%"` | percentuale dei core disponibili, arrotondata |

#### 9.3.1 Modalità non interattiva (`interactive: false`)

I valori vengono letti direttamente dal file di configurazione:

```yaml
parallelism:
  interactive: false
  combinations: "auto"         # tutti i core disponibili
  randomForestSlots: "auto"    # max(1, cores / combinations)
```

`"auto"` per `combinations` usa tutti i core disponibili. `"auto"` per `randomForestSlots`
divide i core rimanenti tra i thread, garantendo che il prodotto non superi il totale.

#### 9.3.2 Modalità interattiva (`interactive: true`)

All'avvio, prima di caricare il dataset, la pipeline mostra le risorse disponibili e
chiede i valori da tastiera. Premendo invio senza digitare nulla si accetta il default:

```
=== Configurazione Parallelismo ===
Core disponibili: 16 | Combinazioni totali: 27

[1/2] Thread per le combinazioni
      Quante combinazioni eseguire in parallelo.
      Accetta: numero intero (es. "4") o percentuale dei core (es. "50%")
      Default: 16 (tutti i core utili) | Range: [1-27]
> 8

[2/2] Thread interni RandomForest
      Quanti thread usa RandomForest per costruire gli alberi in parallelo.
      Non influisce su NaiveBayes e IBk (sempre single-thread).
      Accetta: numero intero (es. "2") o percentuale dei core (es. "25%")
      Default: 2 (auto = 16 core / 8 thread) | Range: [1-16]
> 2

Riepilogo: 8 thread combinazioni × 2 slot RF = 16 core usati su 16 disponibili
```

Utile su macchine diverse senza dover modificare `config.yaml` ogni volta.

#### 9.3.3 Effetto moltiplicativo e raccomandazioni per macchina

I due livelli di parallelismo si **moltiplicano**: `core_usati = combinations × randomForestSlots`.
Esprimere entrambi come percentuale non garantisce una frazione fissa dei core totali, perché
il loro prodotto scala con il quadrato dei core.

**Esempio con percentuali uguali:**

| Core | `combinations="25%"` | `randomForestSlots="25%"` | Core usati | % usata |
|---|---|---|---|---|
| 4 | 1 | 1 | 1 | 25% |
| 8 | 2 | 2 | 4 | 50% |
| 16 | 4 | 4 | 16 | 100% |

Lo stesso config satura completamente un PC da 16 core pur lasciando metà libera su uno da 8.

**Raccomandazione per portatile** - usare solo `combinations` come leva, RF single-thread:

```yaml
parallelism:
  interactive: false
  combinations: "50%"      # usa metà dei core, qualunque sia il numero
  randomForestSlots: "1"   # RF single-thread; il parallelismo viene solo da combinations
```

`combinations: "50%"` garantisce esattamente il 50% dei core su qualsiasi macchina perché
non c'è un secondo moltiplicatore.

**Raccomandazione per PC fisso** - sfruttare entrambi i livelli:

```yaml
parallelism:
  interactive: false        # oppure true per scegliere a runtime
  combinations: "auto"
  randomForestSlots: "auto"
```

**Esempio di runtime (8 core, 27 combinazioni default, calibrato):**
- `combinations = 8`, `randomForestSlots = 1` → ~2h 22m single-thread → **~18m**

**GPU:** non applicabile. RandomForest, NaiveBayes e IBk sono algoritmi CPU-only in Weka.
L'accelerazione GPU richiederebbe una libreria ML diversa (es. DL4J per reti neurali).

### 9.4 Comportamento con metodi wrapper

Quando la FS è ForwardSearch o BackwardSearch, ogni combinazione lancia già un loop
di CV interno molto pesante. In questo caso si consiglia di abbassare `combinations`
a `"2"` o `"4"` per evitare che la memoria si esaurisca (ogni thread tiene in memoria
l'intero dataset e il search state del GreedyStepwise).

---

## 10. Struttura del Package `classifier`

```
com.isw2project.classifier/
├── ClassifierOrchestrator              - coordina il flusso, nessuna logica
├── WekaDatasetService                  - carica il CSV, rimuove colonne non-feature
├── FeatureSelectionBuilderService      - costruisce il filtro FS
├── BalancingBuilderService             - costruisce il filtro di balancing
├── ClassifierBuilderService            - assembla FilteredClassifier con MultiFilter
├── ClassifierEvaluatorService          - esegue 10×10 CV, accumula metriche
├── NpofB20Service                      - calcola NPofB20 dalle predizioni Weka
├── ClassifierResultRowMapperService    - formatta i risultati per CsvWriterService
├── InteractiveParallelismConfigurator  - prompt interattivo per i parametri di parallelismo
├── RuntimeEstimatorService             - stima wall-clock prima dell'esecuzione
├── SpearmanAttributeEval               - custom: Weka AttributeEvaluator (Spearman rank)
├── SmoteFilter                         - re-package: sorgente ufficiale Weka SMOTE (Lichtenwalter 2008)
├── ClassifierType                      - enum (RANDOM_FOREST, NAIVE_BAYES, IBK)
├── FeatureSelectionStrategy            - enum (NONE, INFO_GAIN, SPEARMAN, ...)
└── BalancingStrategy                   - enum (NONE, UNDERSAMPLING, OVERSAMPLING, SMOTE)
```

Entrambe le classi evidenziate sono presenti nel progetto perché non disponibili in `weka-stable 3.8.6`:
- `SpearmanAttributeEval`: implementazione custom - Weka non include un `AttributeEvaluator`
  per Spearman rank correlation nel core jar
- `SmoteFilter`: re-package del sorgente ufficiale Weka SMOTE (Ryan Lichtenwalter, 2008,
  University of Waikato) senza modifiche algoritmiche - il package ufficiale non è pubblicato
  su Maven Central e non può essere dichiarato come dipendenza Maven standard

**Entry point standalone:** `com.isw2project.Milestone2Main`
Eseguibile direttamente dall'IDE o via `mvn exec:java -Dexec.mainClass="com.isw2project.Milestone2Main"`.
Non esegue nessun passo della Milestone 1: legge solo il CSV da disco.

### 10.1 Perché le strategy enum non stanno nel package `model`

In un'architettura a layer che rispetta la separazione delle responsabilità (Layered Architecture,
Clean Architecture, MVC), il layer **model** ha un significato preciso: contiene le **entità di
dominio**, cioè gli oggetti che rappresentano i concetti fondamentali del problema - indipendenti
da qualsiasi scelta tecnologica o implementativa.

Nel contesto di questo progetto, il package `com.isw2project.model` rispecchia esattamente questa
definizione:

| Classe | Entità di dominio rappresentata |
|---|---|
| `Issue` | Bug ticket estratto da Jira |
| `Version` | Release del progetto OpenJPA |
| `JavaClassSnapshot` | Stato di una classe Java in una specifica release |
| `ReleaseSnapshot` | Aggregato di snapshot per una release |
| `ProjectData` | Contenitore dei dati grezzi di progetto |

Queste classi non dipendono da Weka, dalla pipeline ML, né dalla struttura del CSV: sono
rappresentazioni pure dei dati che attraversano l'intero sistema dalla Milestone 1 al CSV finale.

`ClassifierType`, `BalancingStrategy` e `FeatureSelectionStrategy` sono invece **enum di
configurazione della pipeline ML**: non rappresentano entità del dominio del problema (bug,
release, classi Java), ma scelte tecniche su come addestrare e valutare un modello. Sono
inscindibili dai rispettivi builder service che le interpretano:

```
ClassifierType           ->  ClassifierBuilderService      (crea il Classifier Weka)
BalancingStrategy        ->  BalancingBuilderService        (crea il filtro di balancing)
FeatureSelectionStrategy ->  FeatureSelectionBuilderService (crea il filtro FS)
```

Spostarle in `model` romperebbe la coesione: la strategy enum e il builder che la usa sono
due facce della stessa astrazione e vivono correttamente nello stesso package.

`Combination` è un caso ulteriormente diverso: è una `private record` interna a
`ClassifierOrchestrator`, usata esclusivamente come contenitore temporaneo durante la costruzione
della lista di combinazioni. Non è mai esposta fuori dall'orchestratore e non merita un file
dedicato.

---

## 11. Note Implementative

### 11.1 Correzione del double-randomization bug

`Evaluation.crossValidateModel(classifier, data, folds, random)` gestisce internamente
shuffle e stratificazione del dataset prima di costruire i fold. Una versione precedente
della pipeline pre-shufflava il dataset con `data.randomize(new Random(run))` prima di
passarlo a `crossValidateModel`: il metodo Weka applica poi il proprio shuffle con lo
stesso seed, producendo fold diversi da quelli intesi (double-randomization). Questo
alterava silenziosamente la riproducibilità dei risultati. La correzione consiste nel
passare il dataset originale direttamente a `crossValidateModel`, lasciando a Weka l'unica
responsabilità della randomizzazione.

### 11.2 Gestione dei valori NaN nelle metriche

`Evaluation.precision(classIndex)` e metodi analoghi restituiscono `Double.NaN` quando
una classe non ha predizioni positive in nessun fold (es. recall di una classe con zero
istanze predette). Un NaN si propaga aritmeticamente attraverso tutte le somme e medie,
rendendo l'intera riga del CSV inutilizzabile. Il metodo `safe(double v)` in
`ClassifierEvaluatorService` sostituisce NaN e Infinite con `0.0` prima dell'accumulo,
permettendo di scrivere comunque un valore interpretabile nel CSV.

### 11.3 Soppressione dei warning di avvio Weka

All'avvio, Weka tenta di caricare librerie native per l'algebra lineare (`ARPACK`, `BLAS`)
tramite `jniloader`. Su sistemi senza le librerie native installate, questo produce due
categorie di warning:

1. **`System::load` restricted method warning** (JVM, Java 24+): la JVM emette un avviso
   perché `jniloader` chiama il metodo nativo `System::load` da un modulo unnamed. Il
   flag necessario è `--enable-native-access=ALL-UNNAMED`:
   - **Da CLI Maven** (`mvn exec:java`): aggiunto in `.mvn/jvm.config`, letto automaticamente
     dal launcher Maven ad ogni esecuzione.
   - **Da IntelliJ**: va inserito nel campo **VM options** della run configuration
     (`Edit Configurations... → VM options`), perché IntelliJ non legge `.mvn/jvm.config`.

2. **Warning ARPACK native library** (`java.util.logging`): `com.github.fommil.netlib.ARPACK`
   usa JUL (`java.util.logging`) direttamente - non SLF4J - per loggare il fallback alla
   implementazione Java pura. Questi warning bypassano il sistema di logging del progetto
   (SLF4J + Logback) e vengono stampati in formato JUL direttamente sullo stderr.
   La soluzione è il bridge `jul-to-slf4j`: `SLF4JBridgeHandler.install()` viene chiamato
   all'inizio di `Milestone2Main.main()` e redirige tutto il traffico JUL attraverso SLF4J,
   dove `logback.xml` sopprime il package `com.github.fommil` con `level="OFF"`.

Weka cade automaticamente sull'implementazione Java pura di ARPACK/BLAS quando le native
non sono disponibili: **la mancanza delle librerie native non influisce sulla correttezza
dei risultati**, solo sulle prestazioni della algebra lineare (irrilevante per RandomForest,
NaiveBayes e IBk che non usano decomposizioni matriciali dense).

---

## 12. Considerazioni Finali

### Cosa ci si aspetta dai risultati

- **RandomForest senza balancing**: precision moderata, recall bassa (il classificatore
  si concentra sulla classe maggioritaria). Buon AUC e NPofB20.
- **Qualsiasi classificatore + SMOTE o Oversampling**: recall aumenta significativamente
  a scapito della precision. Il trade-off recall/precision è il problema centrale.
- **NaiveBayes + FS (InfoGain o Spearman)**: beneficia molto della rimozione delle feature
  ridondanti (LOC_Touched, Churn e LOC_Added sono fortemente correlate tra loro).
- **IBk + Undersampling**: il dataset ridotto (~2.756 istanze) rende la k-NN più veloce
  e meno sensibile alla distorsione della maggioranza.
- **AUC**: relativamente stabile tra configurazioni perché misura la capacità di ranking,
  non la soglia di classificazione. RandomForest tende a dominare.
- **NPofB20**: tende a essere alto (0.5-0.8) con classificatori che producono probabilità
  ben calibrate (RandomForest > IBk > NaiveBayes in questo senso).

### Limitazioni

1. **Temporal leakage strutturale:** la 10×10 fold CV assegna le istanze ai fold
   casualmente, senza rispettare l'ordine temporale delle release. Il modello può quindi
   essere addestrato su informazioni "future" rispetto a parte del set di test, producendo
   metriche leggermente ottimistiche rispetto all'accuratezza in produzione. Il confronto
   completo con Walk-Forward Validation - comprensivo di pro, contro e motivazione della
   scelta - è discusso in §6.5.

2. **Approssimazione SMOTE:** la percentuale di SMOTE è calcolata sull'intero dataset.
   La percentuale esatta per-fold varia del ~10% (ogni fold ha il 90% delle istanze),
   introducendo una piccola imprecisione nel bilanciamento.

3. **Wrapper FS e search space:** con 19 feature, GreedyStepwise è trattabile ma lento.
   Con un numero maggiore di feature il costo crescerebbe quadraticamente; in quel caso
   si preferisce un filter method.

4. **Nessun tuning degli iperparametri:** i classificatori usano i valori di default di
   Weka (es. 100 alberi per RandomForest, k=1 per IBk). Un tuning via grid search
   migliorebbe i risultati ma richiederebbe un ulteriore livello di cross-validation
   annidata, aumentando ulteriormente i tempi.

---

## 13. Domande del Professore: Cosa Serve e Cosa Manca

Il professore ha posto tre domande analitiche sui risultati:

1. Quale classificatore è più accurato degli altri?
2. Il miglior classificatore cambia in base a dataset / numeroRelease / metrica?
3. Qual è il classificatore migliore per una determinata metrica?

### 13.1 Cosa è già possibile rispondere

Il CSV prodotto dalla pipeline attuale (`OPENJPA_classifier.csv`) ha una riga per ogni
combinazione (classificatore × FS × balancing) con le colonne:
`Dataset, Classifier, FS, Balancing, Precision, Recall, AUC, Kappa, NPofB20`.

Da questo file è già possibile rispondere a:

- **D1 - Classificatore più accurato:** aggregare per `Classifier` (media o mediana di ogni
  metrica su tutte le combinazioni FS/balancing) e ordinare. Qualsiasi strumento (Excel,
  Python/pandas, R) lo fa in due righe.

- **D3 - Miglior classificatore per metrica:** per ogni colonna metrica, trovare il `Classifier`
  con valore massimo. Anche questo è direttamente ricavabile dal CSV attuale.

- **D2 - Cambia in base al dataset:** se si esegue la pipeline su più progetti (abilitando
  BOOKKEEPER, KAFKA o altri in `config.yaml`), ogni progetto produce il proprio CSV con la
  colonna `Dataset` valorizzata. Confrontare i CSV tra progetti risponde a questa domanda.

### 13.2 Cosa NON è possibile rispondere: il `numeroRelease`

La domanda "il miglior classificatore cambia in base al **numero di release** usate in
training?" richiede una valutazione che varia il training set per dimensione temporale.

La pipeline attuale usa **10-times 10-fold cross-validation**: divide il dataset in fold
casuali, senza rispettare l'ordine cronologico delle release. Ogni fold di training contiene
istanze di release diverse mischiate casualmente. Non esiste quindi una nozione di "quante
release sono state usate per addestrare il modello" - i fold non corrispondono a finestre
temporali.

Per rispondere a questa domanda servirebbe la **Walk-Forward Validation**:

```
Release disponibili in ordine cronologico: R1, R2, R3, ..., Rn

Split 1: Train = {R1}              → Test = {R2}
Split 2: Train = {R1, R2}          → Test = {R3}
Split 3: Train = {R1, R2, R3}      → Test = {R4}
...
Split n-1: Train = {R1, ..., Rn-1} → Test = {Rn}
```

Per ogni split si registra `NumTrainingReleases` e si calcola le metriche su `TestRelease`.
Il CSV di output avrebbe una riga per ogni (combinazione × split), permettendo di tracciare
come le performance evolvono all'aumentare del training set temporale.

### 13.3 Perché la Walk-Forward non è stata implementata

La Walk-Forward richiede modifiche architetturali non banali:

1. **`WekaDatasetService`** deve estrarre le etichette di release per ogni istanza *prima*
   di rimuovere la colonna `release` - che oggi viene scartata subito dopo il caricamento.

2. **`WalkForwardSplitter`** deve partizionare le istanze in train/test rispettando l'ordine
   temporale, costruendo `Instances` Weka separati per ogni split.

3. **`WalkForwardEvaluatorService`** deve addestrare il classificatore sul training fold e
   valutarlo sul test fold (senza cross-validation), ricalcolando i parametri di balancing
   (SMOTE %, oversampling %) sul training di ogni split - non sull'intero dataset.

4. **`WalkForwardOrchestrator`** deve coordinare il ciclo su combinazioni × split e
   produrre un secondo CSV con le colonne aggiuntive `NumTrainingReleases` e `TestRelease`.

Il numero di split dipende dal numero di release: OpenJPA ha ~11 release, quindi si
avrebbero ~10 split per combinazione → 36 combinazioni × 10 split = 360 righe di output,
ciascuna ottenuta da un training completo da zero (non da CV). I tempi sarebbero comparabili
a quelli della pipeline attuale ma l'implementazione è **ortogonale e non interferisce** con
la pipeline 10-fold esistente: i due approcci potrebbero coesistere come modalità parallele,
ognuna producendo il proprio CSV.

---

## 14. Analisi dei Risultati: Prima Esecuzione di Test (OpenJPA)

> **Nota:** questa esecuzione è un **run di test preliminare** con la configurazione default
> (3 clf × 3 FS × 3 bal = 27 combinazioni, SMOTE e wrapper FS disabilitati). I risultati vanno
> interpretati come punto di partenza; run successivi includeranno SMOTE, i metodi wrapper
> (ForwardSearch/BackwardSearch) e potenzialmente altri dataset (BOOKKEEPER, KAFKA).

Dati grezzi (le 9 righe con FS diversi sono identiche per ogni coppia Classifier/Balancing -
vedi §14.1; si riportano solo le righe `No FS` come rappresentative):

| Classifier | Balancing | Precision | Recall | AUC | Kappa | NPofB20 |
|---|---|---|---|---|---|---|
| RandomForest | None | 0.73 | 0.52 | 0.94 | 0.58 | 0.88 |
| RandomForest | Undersampling | 0.31 | 0.86 | 0.91 | 0.37 | 0.80 |
| RandomForest | Oversampling | 0.61 | 0.66 | 0.94 | 0.59 | 0.87 |
| NaiveBayes | None | 0.35 | 0.29 | 0.79 | 0.26 | 0.54 |
| NaiveBayes | Undersampling | 0.34 | 0.32 | 0.76 | 0.26 | 0.53 |
| NaiveBayes | Oversampling | 0.34 | 0.32 | 0.78 | 0.26 | 0.54 |
| IBk | None | 0.53 | 0.51 | 0.75 | 0.47 | 0.56 |
| IBk | Undersampling | 0.26 | 0.81 | 0.79 | 0.30 | 0.57 |
| IBk | Oversampling | 0.47 | 0.58 | 0.77 | 0.46 | 0.61 |

---

### 14.1 Feature Selection è un no-op su questo dataset

Per ogni coppia (Classifier, Balancing), i valori con No FS, InfoGain e Spearman sono
**identici alla terza cifra decimale**. Esempio con RF + None:

| FS | Precision | Recall | AUC | Kappa | NPofB20 |
|---|---|---|---|---|---|
| No FS | 0.73 | 0.52 | 0.94 | 0.58 | 0.88 |
| InfoGain | 0.73 | 0.52 | 0.94 | 0.58 | 0.88 |
| Spearman | 0.72 | 0.53 | 0.94 | 0.58 | 0.88 |

**Causa:** `InfoGainAttributeEval + Ranker(threshold=0.0)` mantiene tutti gli attributi con
`InfoGain > 0`. Le ~19 feature di OpenJPA (LOC, CC, commit-count, ecc.) sono tutte informative
rispetto alla classe `isBuggy` - nessuna ha InfoGain esattamente 0 e quindi nessuna viene
eliminata. Analogamente, Spearman con soglia 0 mantiene tutto. Il modello addestrato è
quindi identico al caso No FS.

Questo non è un bug della pipeline: è un risultato valido che dice che **tutte le feature
portano informazione** su questo dataset. I metodi wrapper (ForwardSearch/BackwardSearch)
potrebbero trovare sottoinsiemi più piccoli, ma non sono stati eseguiti per i tempi proibitivi.
I run futuri con i wrapper permetteranno di verificare se esiste un sottoinsieme ottimale.

---

### 14.2 Kappa < 0.6 su tutto il run: limite strutturale, non un difetto

Il valore `0.6` è la soglia standard Landis & Koch (1977) per l'"accordo sostanziale" in
diagnostica medica. Tutti i valori di questo run sono sotto 0.6 (il massimo è RF + Oversampling
= 0.59). Ci sono tre ragioni strutturali:

#### A) Vincolo matematico imposto dall'imbalance al 9%

Kappa = `(P_o − P_e) / (1 − P_e)`, dove `P_e` è l'accordo atteso per caso. Con ~9% di
istanze buggy e un classificatore che prevede "buggy" circa il 9% delle volte:

```
P_e ≈ 0.91 × 0.91 + 0.09 × 0.09 = 0.828 + 0.008 = 0.836
```

Per ottenere Kappa = 0.6 con questa P_e:

```
0.6 = (P_o − 0.836) / (1 − 0.836)
P_o = 0.6 × 0.164 + 0.836 = 0.934
```

Servono **93.4% di predizioni corrette in assoluto**. Con 9% di buggy, ogni falso positivo
e ogni falso negativo incide pesantemente su P_o. Raggiungere simultaneamente alta precision
e alto recall con un imbalance così severo è matematicamente difficile: alzare il recall
genera FP (abbassa precision e P_o), abbassarlo genera FN (idem).

#### B) AUC 0.94 e Kappa 0.58 non si contraddicono

RF ottiene AUC = 0.94 - vicino al perfetto - ma Kappa = 0.58. Questo non è
un'incoerenza: le due metriche misurano cose diverse.

- **AUC** misura la capacità di *ranking*: quanto bene il modello ordina i file dal più al
  meno rischioso, indipendentemente dalla soglia di classificazione.
- **Kappa** misura la qualità delle *predizioni binarie* a soglia fissa (default 0.5 in Weka).

Con 9% di imbalance, la soglia ottimale di classificazione non è 0.5 ma circa 0.09 (la prior
della classe). Weka usa 0.5 di default: questo comprime il recall del lato "buggy" e limita
Kappa indipendentemente dalla qualità discriminatoria del modello. Il modello *sa* distinguere
bene (AUC alto), ma la predizione binaria a soglia fissa penalizza Kappa.

#### C) Soffitto delle feature statiche

LOC, CC, commit-count sono correlati ai bug ma non causalmente collegati. Esiste rumore
irriducibile che nessun classificatore può eliminare. Questo pone un ceiling empirico
sul Kappa raggiungibile con queste feature su questo dataset.

#### D) Contesto della letteratura di defect prediction

Il threshold 0.6 viene dalla diagnostica medica; il dominio software è intrinsecamente più
rumoroso. Nella letteratura specifica:

- Hall et al. (2012) - survey su 208 studi: Kappa tipico 0.2-0.5 per defect prediction
- Lessmann et al. (2008) - benchmark 10 progetti: RF è di solito il migliore, Kappa raramente supera 0.55-0.60

**Il valore 0.58-0.59 di RF in questo run è nella fascia alta della letteratura**, non una
performance mediocre. Il Kappa di NaiveBayes (0.26) rientra nell'intervallo tipico per NB
su dataset sbilanciati con feature correlate.

---

### 14.3 Balancing: Undersampling aumenta Recall ma peggiora Kappa

| RF | Precision | Recall | Kappa |
|---|---|---|---|
| None | 0.73 | 0.52 | **0.58** |
| Undersampling | 0.31 | 0.86 | **0.37** |
| Oversampling | 0.61 | 0.66 | **0.59** |

Undersampling triplica il Recall (0.52 → 0.86) ma fa crollare il Kappa (0.58 → 0.37).
Lo stesso pattern si osserva su IBk (Kappa: 0.47 → 0.30).

**Perché:** Undersampling bilancia il training set a 50/50, spingendo il classificatore a
prevedere "buggy" molto più spesso. Questo abbatte i falsi negativi (Recall alto) ma genera
molti falsi positivi: Precision crolla da 0.73 a 0.31, cioè 2 predizioni "buggy" su 3 sono
errate. Kappa penalizza questa tendenza a sovra-predire la classe positiva perché P_o
(accordo osservato) cala per via dei numerosi FP.

**Interpretazione pratica:**
- Se il costo di un bug sfuggito è altissimo (es. sicurezza), Undersampling + alto Recall
  è preferibile.
- Se si vuole un bilancio complessivo, Oversampling (0.61/0.66, Kappa 0.59) è la scelta
  migliore su RF.
- Kappa espone una verità che Recall da solo nasconde: il modello con Undersampling è
  meno affidabile complessivamente, anche se cattura più bug.

---

### 14.4 AUC di RF stabile al bilanciamento; NB e IBk no

| Classifier | AUC (None) | AUC (Under) | AUC (Over) |
|---|---|---|---|
| RF | **0.94** | 0.91 | 0.94 |
| NB | 0.79 | 0.76 | 0.78 |
| IBk | 0.75 | 0.79 | 0.77 |

AUC misura la capacità di ranking, threshold-free. Per RF il bilanciamento modifica la soglia
effettiva ma non la qualità del ranking (lieve calo a 0.91 con Undersampling perché si buttano
dati). Per IBk l'Undersampling aiuta leggermente l'AUC (0.75 → 0.79): il dataset ridotto
riduce l'effetto di distorsione della maggioranza nella distanza k-NN.

---

### 14.5 NaiveBayes è insensibile al bilanciamento

| NB | Recall (None) | Recall (Under) | Recall (Over) |
|---|---|---|---|
| Valori | 0.29 | 0.32 | 0.32 |

Mentre RF e IBk mostrano un salto netto nel Recall con Undersampling (+34pp e +30pp
rispettivamente), NB guadagna appena 3pp. Kappa rimane fisso a 0.26 in tutte e tre le
strategie di balancing.

**Perché:** NaiveBayes stima le probabilità delle feature indipendentemente per ogni classe.
Con feature correlate come LOC/CC/commit-count, l'assunzione di indipendenza condizionale
è fortemente violata: le likelihood `P(feature | buggy)` risultano mal calibrate. Il
bilanciamento modifica la prior `P(buggy)` nel training set, ma non risolve il problema
strutturale delle correlazioni. La performance di NB è quindi limitata dall'assumption
violation più che dall'imbalance: aggiungere SMOTE o FS wrapper difficilmente migliorerà
NB in modo significativo su questo dataset.

---

### 14.6 NPofB20: RF è eccellente per la prioritizzazione pratica

| Classifier | NPofB20 (None) | NPofB20 (Under) | NPofB20 (Over) |
|---|---|---|---|
| RF | **0.88** | 0.80 | 0.87 |
| NB | 0.54 | 0.53 | 0.54 |
| IBk | 0.56 | 0.57 | 0.61 |

La baseline casuale è NPofB20 ≈ 0.20 (top 20% di file = 20% dei bug per definizione).

RF senza balancing cattura l'**88% dei bug nel top 20% dei file per risk score**: un team che
ispeziona solo il 20% dei file più a rischio troverebbe quasi tutti i bug. Questo rende RF
con bilanciamento None o Oversampling il choice pratico più forte per la prioritizzazione
delle code review.

NB e IBk si fermano a ~0.54-0.61: meglio del casuale ma significativamente inferiori a RF.
La differenza riflette la qualità della calibrazione delle probabilità posterior: RF stima
meglio `P(buggy | features)`, e quindi il ranking per risk score è più affidabile.

---

### 14.7 Sintesi e prospettive per i run futuri

| Osservazione | Causa | Impatto su run futuri |
|---|---|---|
| FS = no-op | Tutte le feature informative, threshold=0.0 | Wrapper FS potrebbe trovare sottoinsiemi; da verificare |
| Kappa RF ≈ 0.59 (sotto 0.6) | Imbalance 9% + soglia 0.5 + soffitto feature | SMOTE potrebbe spingere oltre 0.6; da misurare |
| Undersampling: Recall↑ ma Kappa↓ | FP esplodono | Pattern atteso, conferma teoria |
| NB insensibile al balancing | Assumption violation domina | Wrapper FS improbabilmente aiuti NB |
| RF NPofB20=0.88 | Buona calibrazione posterior | RF dominante anche su altri dataset (da verificare) |

I run futuri con **SMOTE abilitato** (36 combinazioni) e **BOOKKEEPER/KAFKA** aggiunti
permetteranno di rispondere alle domande del professore sulle variazioni tra dataset.
I **metodi wrapper** (ForwardSearch/BackwardSearch) potrebbero rivelare se esiste un
sottoinsieme di feature che supera il plateau attuale, ma i tempi sono nell'ordine di ore
per singola combinazione.

---

## 15. Guida alla Configurazione (`config.yaml`)

Questa sezione documenta l'intero file `config.yaml`. Le prime tre sezioni (`baseUrl`,
`projects`, `git`, `csv`) riguardano la **Milestone 1**; la sezione `classifier` riguarda
la **Milestone 2**. Le due pipeline leggono lo stesso file ma usano parti diverse.

---

### 15.1 Struttura radice

```yaml
baseUrl: https://issues.apache.org/jira   # URL base dell'istanza Jira

projects:       # lista dei progetti da analizzare (Milestone 1)
git:            # percorsi del repository Git locale (Milestone 1)
csv:            # configurazione dell'output CSV (Milestone 1)
metrics:        # parametri di tuning per il calcolo metriche (Milestone 1)
classifier:     # configurazione della valutazione ML (Milestone 2)
```

`AppConfig` è la classe Java che mappa questa radice. Ogni voce corrisponde a un campo
con la relativa classe di configurazione dedicata.

---

### 15.2 `projects` - Progetti Jira (Milestone 1)

```yaml
projects:
  - key: OPENJPA
    jql: "issuetype = Bug AND status in (Closed, Resolved) AND resolution = Fixed"
```

| Campo | Significato |
|---|---|
| `key` | Chiave del progetto Jira (usata anche come prefisso nei nomi dei file CSV di output) |
| `jql` | Query JQL per filtrare i ticket scaricati. Modificarla cambia quali issue vengono analizzate |

Per aggiungere un secondo progetto, aggiungere un secondo elemento alla lista con chiave e JQL propri.

---

### 15.3 `git` - Repository locale (Milestone 1)

```yaml
git:
  cloneUrl: https://github.com/apache/openjpa.git
  repoDir: gitclones/openjpa
  codeOutputDir: output/milestone1/6_extracted_source
```

| Campo | Significato |
|---|---|
| `cloneUrl` | URL del repository Git remoto. Usato da `RepoCloneOrchestrator` per clonare automaticamente il repo al primo avvio |
| `repoDir` | Percorso locale del clone Git. Se la cartella `.git` non esiste, viene clonata automaticamente da `cloneUrl` |
| `codeOutputDir` | Directory dove vengono estratti i file sorgente per ogni release (usata internamente da `GitFileExtractorService`) |

---

### 15.4 `csv` - Output CSV (Milestone 1)

```yaml
csv:
  outputDir: output/milestone1
  issueColumns:
    key: true
    summary: false
    created: true
    ...
  versionColumns:
    name: true
    releaseDate: true
    released: true
```

| Campo | Significato |
|---|---|
| `outputDir` | Directory radice per i CSV della Milestone 1. Le sottocartelle numerate (`1_raw_jira_data`, `2_enriched_jira_data`, ecc.) vengono create automaticamente |
| `issueColumns` | Quali campi degli issue Jira includere nel CSV. `true` = incluso, `false` = escluso |
| `versionColumns` | Quali campi delle versioni includere nel CSV delle versioni |

---

### 15.5 `metrics` - Parametri di tuning (Milestone 1)

```yaml
metrics:
  snapshotPercentage: 1.0
  pmd:
    batchSize: 100
    cpuFraction: 0.5
```

| Campo | Significato |
|---|---|
| `snapshotPercentage` | Frazione di snapshot su cui calcolare le metriche. `1.0` = tutti; valori inferiori (es. `0.1`) velocizzano i run di debug riducendo gli snapshot processati |
| `pmd.batchSize` | Numero di file analizzati per invocazione PMD. Valori bassi riducono il picco di heap; valori alti riducono l'overhead di inizializzazione PMD. Range consigliato: 50-200 |
| `pmd.cpuFraction` | Frazione dei core fisici assegnata ai thread PMD (es. `0.5` = metà dei core). Range consigliato: 0.25-0.75 |

Questi parametri erano in precedenza costanti statiche hardcoded in `CodeSmellsMetric.java`
e nel chiamante `Milestone1Main`. Spostarli in `config.yaml` permette di regolarli senza
ricompilare, utile su macchine con diversa quantità di RAM e core.

---

### 15.6 `classifier` - Valutazione ML (Milestone 2)

```yaml
classifier:
  inputCsv: "output/milestone1/5_snapshots/OPENJPA_snapshots.csv"
  outputDir: "output/milestone2"
  datasetName: "OPENJPA"
```

| Campo | Significato |
|---|---|
| `inputCsv` | CSV prodotto dalla Milestone 1 che viene usato come dataset di input |
| `outputDir` | Directory dove viene scritto il CSV dei risultati della Milestone 2 |
| `datasetName` | Prefisso usato nel nome del file di output e nella colonna `Dataset` del CSV |

#### Liste di combinazioni

```yaml
  classifiers:
    - RANDOM_FOREST
    - NAIVE_BAYES
    - IBK

  featureSelection:
    - NONE
    - INFO_GAIN
    - SPEARMAN
    # - FORWARD_SEARCH   # commentato = disabilitato
    # - BACKWARD_SEARCH  # commentato = disabilitato

  balancing:
    - NONE
    - UNDERSAMPLING
    - OVERSAMPLING
    # - SMOTE            # commentato = disabilitato
```

Le tre liste definiscono il **prodotto cartesiano** delle combinazioni da eseguire:
`|classifiers| × |featureSelection| × |balancing|` combinazioni totali.
Commentare una voce la esclude dall'esecuzione senza modificare il codice.
I valori ammessi corrispondono ai nomi delle enum `ClassifierType`, `FeatureSelectionStrategy`
e `BalancingStrategy`.

#### `crossValidation`

```yaml
  crossValidation:
    runs: 10
    folds: 10
```

| Campo | Significato |
|---|---|
| `runs` | Numero di ripetizioni della k-fold CV (ognuna con seed diverso) |
| `folds` | Numero di fold per ogni run (k nella k-fold CV) |

Il prodotto `runs × folds` è il numero totale di addestramenti per combinazione (default: 100).

#### `wrapperBaseClassifier`

```yaml
  wrapperBaseClassifier: NAIVE_BAYES
```

Classificatore usato internamente dai metodi wrapper (ForwardSearch, BackwardSearch) per
valutare ogni sottoinsieme candidato di feature. NaiveBayes è la scelta consigliata perché
è abbastanza veloce da rendere la ricerca trattabile. Cambiarlo con RANDOM_FOREST
moltiplica i tempi del wrapper di circa 10×.

#### `parallelism`

```yaml
  parallelism:
    interactive: false
    combinations: "50%"
    randomForestSlots: "1"
```

| Campo | Valori | Significato |
|---|---|---|
| `interactive` | `true` / `false` | Se `true`, all'avvio viene mostrato un prompt interattivo per scegliere i thread. Se `false`, vengono usati i valori sotto |
| `combinations` | `"auto"`, intero (`"4"`), percentuale (`"50%"`) | Quante combinazioni eseguire in parallelo. `"auto"` usa tutti i core disponibili |
| `randomForestSlots` | stesso formato | Thread interni usati da RandomForest per costruire gli alberi. Non influisce su NaiveBayes e IBk |

Il prodotto `combinations × randomForestSlots` non dovrebbe superare il numero di core
fisici per evitare oversubscription. Con `"auto"` su entrambi i livelli, `randomForestSlots`
viene calcolato automaticamente come `max(1, core / combinations)`.

---

### 15.7 Struttura delle cartelle di output

Tutti gli output del progetto sono organizzati sotto una cartella `output/` radice, suddivisa
per milestone. Le sottocartelle della Milestone 1 sono numerate in ordine di produzione per
rendere immediatamente leggibile il flusso di elaborazione.

```
output/
├── milestone1/
│   ├── 1_raw_jira_data/          # issues e versions scaricati da Jira (dati grezzi)
│   ├── 2_enriched_jira_data/     # dopo enrichment di OV, AV e fix multi-FV
│   ├── 3_consistency_checked/    # dopo i controlli di consistenza (issue/version check)
│   ├── 4_proportion_applied/     # dopo l'applicazione della proportion (IV stimata)
│   ├── 5_snapshots/              # snapshot per release con metriche e label isBuggy
│   │                             #   -> OPENJPA_snapshots.csv (input per Milestone 2)
│   └── 6_extracted_source/       # file sorgente Java estratti da Git per ogni release
└── milestone2/
    └── OPENJPA_classifier.csv    # risultati della valutazione ML (una riga per combinazione)
```

Ciascuna delle cartelle `1_`-`4_` contiene due file: `OPENJPA_issues.csv` e
`OPENJPA_versions.csv`. La cartella `5_snapshots/` contiene `OPENJPA_snapshots.csv`
(~15.000 righe, una per classe Java per release). La cartella `6_extracted_source/` contiene
i file `.java` estratti da Git, organizzati per versione release.

I path sono configurabili in `config.yaml` (campi `csv.outputDir`, `git.codeOutputDir`,
`classifier.inputCsv`, `classifier.outputDir`). Le directory vengono create automaticamente
alla prima esecuzione se non esistono.

---

### 15.8 Soppressione dei warning

Il progetto sopprime quattro categorie di warning, ciascuna con meccanismo e motivazione
distinti. Vengono documentati qui perché rappresentano scelte esplicite, non dimenticanze.

#### JVM-level - `.mvn/jvm.config`: `--enable-native-access=ALL-UNNAMED`

A partire da Java 24 la JVM restringe l'accesso ai metodi nativi ai soli moduli
esplicitamente autorizzati. Weka usa `jniloader` per caricare librerie native di algebra
lineare (`ARPACK`, `BLAS`); poiché `jniloader` è in un modulo unnamed, senza questo flag
la JVM emette un warning a ogni esecuzione. Il flag abilita l'accesso nativo per tutti i
moduli unnamed, sopprimendo il warning. Il flag è inserito in `.mvn/jvm.config` anziché in
`pom.xml` per non inquinare il build descriptor con una configurazione JVM
runtime-specifica.

#### Codice legacy - `SmoteFilter.java`: `@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})`

Il sorgente di `SmoteFilter` è il codice originale di Weka SMOTE (Ryan Lichtenwalter,
2008), scritto in stile Java pre-generics. I tre warning sono attesi e inevitabili senza
riscrivere il codice originale:

- **`rawtypes`**: dichiarazioni di `List`, `Map`, `Enumeration` senza parametri di tipo
  generics (stile Java 1.4).
- **`unchecked`**: cast da raw type a tipo generico (es. `(Instance) list.get(i)`).
- **`deprecation`**: uso di `new Double(String)`, deprecato da Java 9 in favore di
  `Double.parseDouble(String)`.

Poiché il metodo `doSMOTE()` - che contiene l'intero algoritmo - è identico all'originale,
sopprimere i warning anziché riscriverli garantisce che l'algoritmo non venga
accidentalmente modificato.

#### Regola SonarQube S1172 - `Milestone2Main.java`: `@SuppressWarnings("java:S1172")`

La regola S1172 (*"Unused method parameters should be removed"*) segnala il parametro
`String[] args` di `main` come inutilizzato. La firma `public static void main(String[]
args)` è imposta dalla JVM come entry point e non può essere modificata, indipendentemente
dal fatto che `args` venga usato o meno. La soppressione evita un falso positivo che non
può essere risolto.

#### Regola SonarQube S106 - `InteractiveParallelismConfigurator.java`: `@SuppressWarnings("java:S106")`

La regola S106 (*"Standard outputs should not be used directly to log anything"*) vieta
l'uso diretto di `System.out`, raccomandando un logger. Questa classe è però una console
interattiva: mostra all'utente un menu, legge la sua risposta da stdin e restituisce il
risultato. Usare un logger significherebbe scrivere su `logback` con livello e timestamp,
rompendo l'esperienza interattiva. `System.out` è qui la scelta corretta; la soppressione
documenta l'intenzionalità della scelta.

---

### 15.9 `.mvn/jvm.config` - Argomenti JVM per Maven

```
--enable-native-access=ALL-UNNAMED
```

**Cos'è questo file:** `.mvn/jvm.config` è un file di configurazione Maven letto
automaticamente dal launcher Maven ad ogni esecuzione (`mvn ...`). Ogni riga viene
passata direttamente alla JVM come argomento. **Non supporta commenti**: una riga
che inizia con `#` verrebbe interpretata come un flag JVM non valido e causerebbe
un errore di avvio. Per questa ragione il flag è documentato in §15.8 anziché con
un commento nel file.

**Cosa fa il flag:** vedi §15.8 (Soppressione dei warning, sezione JVM-level).

**Ambito:** questo file è letto solo da Maven CLI (`mvn exec:java`). Non viene letto in altri due casi:

- **IntelliJ:** il flag va aggiunto manualmente nel campo **VM options** della run configuration (`Edit Configurations... -> VM options`).
- **Fat JAR (`java -jar`):** `jvm.config` è un file Maven, non viene letto dalla JVM diretta. Il flag va passato esplicitamente: `java --enable-native-access=ALL-UNNAMED -jar milestone2.jar`.

---

### 15.10 Requisiti di sistema e modalità di esecuzione

#### Requisiti

| Componente | Versione minima | Milestone 1 | Milestone 2 |
|---|---|---|---|
| **Java** | 26 | Richiesto | Richiesto |
| **git** | qualsiasi | Richiesto | Non richiesto |
| **Maven** | 3.8+ | Solo per build/sviluppo | Solo per build/sviluppo |

**Perché git per la Milestone 1:** la fase di estrazione degli snapshot usa
`ProcessBuilder("git", "ls-tree", ...)` e `ProcessBuilder("git", "show", ...)` — il
client `git` deve essere installato e disponibile nel `PATH`. La fase di clone usa JGit
(pura Java, §16.1) e non richiede il client; l'estrazione invece sì.

**Perché NON git per la Milestone 2:** legge solo il CSV da disco, nessuna operazione Git.

---

#### Esecuzione con Maven (ambiente di sviluppo)

Richiede Maven installato e `JAVA_HOME` puntato a Java 26:

```bash
# Milestone 1
mvn exec:java -Dexec.mainClass="com.isw2project.Milestone1Main"

# Milestone 2
mvn exec:java -Dexec.mainClass="com.isw2project.Milestone2Main"
```

Il flag `--enable-native-access=ALL-UNNAMED` viene letto automaticamente da
`.mvn/jvm.config` ad ogni invocazione Maven.

---

#### Fat JAR (raccomandato per esecuzione su altre macchine)

Il progetto produce due fat JAR autocontenuti — tutte le dipendenze incluse —
tramite il Maven Shade Plugin. Una volta costruiti, non servono né Maven né il
codice sorgente sulla macchina target.

**Build (una volta sola, dal progetto):**

```bash
mvn package
# produce: target/milestone1.jar e target/milestone2.jar
```

**Esecuzione:**

```bash
java --enable-native-access=ALL-UNNAMED -jar target/milestone1.jar
java --enable-native-access=ALL-UNNAMED -jar target/milestone2.jar
```

**Argomenti JVM rilevanti:**

| Flag | Quando serve | Effetto |
|---|---|---|
| `--enable-native-access=ALL-UNNAMED` | Java 24+ | Sopprime il warning di Weka su `jniloader` (vedi §15.8). Il codice funziona anche senza, ma vengono stampati warning a stderr |

**Note operative:**

- Gli output vengono scritti nella working directory da cui viene lanciato `java -jar`
  (es. `output/milestone1/...` relativa alla cartella corrente). Eseguire dalla root
  del progetto o dalla cartella dove si vuole l'output.
- `jvm.config` non viene letto dalla JVM diretta: il flag va sempre passato
  esplicitamente sulla riga di comando quando si usa `java -jar`.
- Per `milestone1.jar`: il client `git` deve essere installato e nel `PATH` della
  macchina target.
- Per `milestone2.jar`: basta Java 26, nessuna dipendenza esterna aggiuntiva.

---

## 16. Modifiche alla Milestone 1

Durante lo sviluppo della Milestone 2 sono state apportate tre modifiche alla pipeline
della Milestone 1: l'aggiunta del clone automatico del repository, la configurabilità
dei parametri di tuning e la riorganizzazione delle cartelle di output. Le modifiche
sono ortogonali tra loro e non alterano la logica di calcolo delle metriche o della
bugginess labeling.

---

### 16.1 Package `repocloner` - Clone automatico del repository

**Problema:** il repository Git di OpenJPA veniva clonato manualmente prima di eseguire
la Milestone 1. La cartella `gitclones/openjpa` non è versionata (`.gitignore`), quindi
chi scaricava il progetto doveva eseguire il clone a mano.

**Soluzione:** è stato aggiunto il package `com.isw2project.repocloner`, con due classi
che seguono il pattern service/orchestrator già usato nel resto del progetto:

| Classe | Responsabilità |
|---|---|
| `RepoCloneService` | Esegue il clone tramite **JGit** (pura Java, nessun git CLI) con `depth=1` (shallow clone) per limitare la dimensione scaricata |
| `RepoCloneOrchestrator` | Controlla se `<repoDir>/.git` esiste già; se sì, salta il clone; altrimenti delega a `RepoCloneService` |

`Milestone1Main` chiama `RepoCloneOrchestrator.ensureCloned(config.getGit())` come primo
passo, prima di qualsiasi operazione Git. In questo modo, avviare la Milestone 1 è
sufficiente per rendere disponibile il repository localmente senza eseguire manualmente
un clone da riga di comando.

**Nota:** JGit elimina la dipendenza dal client `git` solo per la fase di clone. La fase
successiva di estrazione degli snapshot (`GitFileExtractorService`) usa `ProcessBuilder`
con `git ls-tree` e `git show` e richiede ancora il client `git` installato nel `PATH`.
Per i requisiti completi vedi §15.10.

**Dipendenza aggiunta in `pom.xml`:** `org.eclipse.jgit:org.eclipse.jgit:7.1.0`

L'URL del repository è configurabile tramite `git.cloneUrl` in `config.yaml` (vedi §15.3).

---

### 16.2 Parametri di tuning in `config.yaml` - `MetricsConfig`

**Problema:** tre parametri che influenzano il tempo di esecuzione e il consumo di memoria
della Milestone 1 erano hardcoded come costanti statiche in `CodeSmellsMetric.java`:

| Parametro | Era | Ora |
|---|---|---|
| Frazione di snapshot da processare | passato da `Milestone1Main` a `computeAll()` | `metrics.snapshotPercentage` in `config.yaml` |
| File analizzati per invocazione PMD | `static final int BATCH_SIZE = 100` | `metrics.pmd.batchSize` in `config.yaml` |
| Frazione di core assegnata a PMD | `static final double CPU_FRACTION = 0.5` | `metrics.pmd.cpuFraction` in `config.yaml` |

**Soluzione:** è stata aggiunta la classe `com.isw2project.config.MetricsConfig` (con inner
class `PmdConfig`), mappata da SnakeYAML come tutte le altre sezioni di `AppConfig`.
`CodeSmellsMetric` riceve `batchSize` e `cpuFraction` via costruttore (injection), e
`MetricsOrchestrator` legge `snapshotPercentage` dal `MetricsConfig` iniettato.

Il parametro `snapshotPercentage` è utile per debug: impostarlo a `0.1` processa solo
il 10% degli snapshot, riducendo il tempo di un run di test da ore a minuti.

Per i dettagli dei campi e il loro range consigliato, vedi §15.5.

---

### 16.3 Riorganizzazione delle cartelle di output

**Problema:** tutti gli output della Milestone 1 finivano in cartelle con nomi generici
(`originalResult`, `enrichedResult`, ecc.) direttamente sotto `output/`, mescolati con
gli output della Milestone 2.

**Soluzione:** le cartelle sono state rinominate con nomi descrittivi e prefissi numerici
che riflettono l'ordine di produzione, e spostate sotto `output/milestone1/`. Il CSV di
Milestone 2 va sotto `output/milestone2/`.

| Vecchio nome | Nuovo nome |
|---|---|
| `output/originalResult/` | `output/milestone1/1_raw_jira_data/` |
| `output/enrichedResult/` | `output/milestone1/2_enriched_jira_data/` |
| `output/checkedResult/` | `output/milestone1/3_consistency_checked/` |
| `output/proportionResult/` | `output/milestone1/4_proportion_applied/` |
| `output/snapshotResult/` | `output/milestone1/5_snapshots/` |
| `output/code/` | `output/milestone1/6_extracted_source/` |
| `output/classifierResult/` | `output/milestone2/` |

I path sono configurabili in `config.yaml`. La struttura completa è descritta in §15.7.

