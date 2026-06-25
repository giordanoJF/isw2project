/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Originally SMOTE.java
 *
 * Copyright (C) 2008 Ryan Lichtenwalter
 * Copyright (C) 2008 University of Waikato, Hamilton, New Zealand
 *
 * Re-packaged here as SmoteFilter in com.isw2project.classifier because the
 * official Weka SMOTE package is not published on Maven Central and cannot be
 * declared as a standard Maven dependency.
 * Reference: https://weka.sourceforge.io/packageMetaData/SMOTE/index.html
 * Algorithm: Chawla et al. (2002), JAIR vol. 16, pp. 321-357.
 *
 * Changes from the original source (purely structural, no algorithmic impact):
 *
 * 1. Package and class name: weka.filters.supervised.instance.SMOTE
 *                         -> com.isw2project.classifier.SmoteFilter
 *
 * 2. Removed Weka GUI annotation blocks (<!-- globalinfo-start -->,
 *    <!-- technical-bibtex-start -->, <!-- options-start --> and their
 *    closing counterparts). These markers are parsed by Weka's Explorer/
 *    Experimenter GUI to auto-populate help text. This project does not
 *    use Weka's GUI, so the blocks serve no purpose.
 *
 * 3. Removed per-method Javadoc on getters/setters and collapsed them to
 *    single-line declarations. The public API is unchanged.
 *
 * 4. Added @SuppressWarnings("unchecked","rawtypes","deprecation") at the
 *    class level to silence compiler warnings caused by the original use of
 *    raw types (pre-generics Java style) and the deprecated Double constructor.
 *
 * The doSMOTE() method - which contains the entire algorithm - is identical
 * to the original (refactored into helper methods for readability).
 */

package com.isw2project.classifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.RevisionUtils;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.SupervisedFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class SmoteFilter
  extends Filter
  implements SupervisedFilter, OptionHandler, TechnicalInformationHandler {

  static final long serialVersionUID = -1653880819059250364L;

  private static final Logger log = LoggerFactory.getLogger(SmoteFilter.class);

  protected int nearestNeighbors = 5;
  protected int randomSeed = 1;
  protected double percentage = 100.0;
  protected String classValueIndex = "0";
  protected boolean detectMinorityClass = true;

  public String globalInfo() {
    return "Resamples a dataset by applying the Synthetic Minority Oversampling TEchnique (SMOTE)."
        + " The original dataset must fit entirely in memory."
        + " The amount of SMOTE and number of nearest neighbors may be specified."
        + " For more information, see \n\n"
        + getTechnicalInformation().toString();
  }

  @Override
  public TechnicalInformation getTechnicalInformation() {
    TechnicalInformation result = new TechnicalInformation(Type.ARTICLE);
    result.setValue(Field.AUTHOR, "Nitesh V. Chawla et. al.");
    result.setValue(Field.TITLE, "Synthetic Minority Over-sampling Technique");
    result.setValue(Field.JOURNAL, "Journal of Artificial Intelligence Research");
    result.setValue(Field.YEAR, "2002");
    result.setValue(Field.VOLUME, "16");
    result.setValue(Field.PAGES, "321-357");
    return result;
  }

  @Override
  public String getRevision() {
    return RevisionUtils.extract("$Revision: 8108 $");
  }

  @Override
  public Capabilities getCapabilities() {
    Capabilities result = super.getCapabilities();
    result.disableAll();
    result.enableAllAttributes();
    result.enable(Capability.MISSING_VALUES);
    result.enable(Capability.NOMINAL_CLASS);
    result.enable(Capability.MISSING_CLASS_VALUES);
    return result;
  }

  @Override
  public Enumeration listOptions() {
    List<Option> newVector = new ArrayList<>();
    newVector.add(new Option(
        "\tSpecifies the random number seed\n\t(default 1)",
        "S", 1, "-S <num>"));
    newVector.add(new Option(
        "\tSpecifies percentage of SMOTE instances to create.\n\t(default 100.0)\n",
        "P", 1, "-P <percentage>"));
    newVector.add(new Option(
        "\tSpecifies the number of nearest neighbors to use.\n\t(default 5)\n",
        "K", 1, "-K <nearest-neighbors>"));
    newVector.add(new Option(
        "\tSpecifies the index of the nominal class value to SMOTE\n\t(default 0: auto-detect non-empty minority class))\n",
        "C", 1, "-C <value-index>"));
    return Collections.enumeration(newVector);
  }

  @Override
  public void setOptions(String[] options) throws Exception {
    String seedStr = Utils.getOption('S', options);
    setRandomSeed(!seedStr.isEmpty() ? Integer.parseInt(seedStr) : 1);

    String percentageStr = Utils.getOption('P', options);
    setPercentage(!percentageStr.isEmpty() ? Double.parseDouble(percentageStr) : 100.0);

    String nnStr = Utils.getOption('K', options);
    setNearestNeighbors(!nnStr.isEmpty() ? Integer.parseInt(nnStr) : 5);

    String classValueIndexStr = Utils.getOption('C', options);
    if (!classValueIndexStr.isEmpty()) {
      setClassValue(classValueIndexStr);
    } else {
      detectMinorityClass = true;
    }
  }

  @Override
  public String[] getOptions() {
    List<String> result = new ArrayList<>();
    result.add("-C"); result.add(getClassValue());
    result.add("-K"); result.add("" + getNearestNeighbors());
    result.add("-P"); result.add("" + getPercentage());
    result.add("-S"); result.add("" + getRandomSeed());
    return result.toArray(new String[0]);
  }

  public String randomSeedTipText()      { return "The seed used for random sampling."; }
  public int    getRandomSeed()          { return randomSeed; }
  public void   setRandomSeed(int value) { randomSeed = value; }

  public String percentageTipText() { return "The percentage of SMOTE instances to create."; }
  public double getPercentage()     { return percentage; }
  public void   setPercentage(double value) {
    if (value >= 0) percentage = value;
    else log.warn("Percentage must be >= 0!");
  }

  public String nearestNeighborsTipText()      { return "The number of nearest neighbors to use."; }
  public int    getNearestNeighbors()           { return nearestNeighbors; }
  public void   setNearestNeighbors(int value) {
    if (value >= 1) nearestNeighbors = value;
    else log.warn("At least 1 neighbor necessary!");
  }

  public String classValueTipText() {
    return "The index of the class value to which SMOTE should be applied. "
        + "Use a value of 0 to auto-detect the non-empty minority class.";
  }
  public String getClassValue() { return classValueIndex; }
  public void   setClassValue(String value) {
    classValueIndex = value;
    detectMinorityClass = classValueIndex.equals("0");
  }

  @Override
  public boolean setInputFormat(Instances instanceInfo) throws Exception {
    super.setInputFormat(instanceInfo);
    super.setOutputFormat(instanceInfo);
    return true;
  }

  @Override
  public boolean input(Instance instance) {
    if (getInputFormat() == null)
      throw new IllegalStateException("No input instance format defined");
    if (m_NewBatch) {
      resetQueue();
      m_NewBatch = false;
    }
    if (m_FirstBatchDone) {
      push(instance);
      return true;
    } else {
      bufferInput(instance);
      return false;
    }
  }

  @Override
  public boolean batchFinished() throws Exception {
    if (getInputFormat() == null)
      throw new IllegalStateException("No input instance format defined");
    if (!m_FirstBatchDone) {
      doSMOTE();
    }
    flushInput();
    m_NewBatch = true;
    m_FirstBatchDone = true;
    return (numPendingOutput() != 0);
  }

  protected void doSMOTE() {
    int[] minority = resolveMinority();
    int minIndex = minority[0];
    int minCount = minority[1];
    int knn = adjustNearestNeighbors(minCount);
    Instances sample = collectMinoritySample(minIndex);
    Map vdmMap = buildVdmMap();
    Random rand = new Random(randomSeed);
    Set extraIndexSet = buildExtraIndexSet(sample, rand);
    synthesize(sample, vdmMap, knn, minIndex, rand, extraIndexSet);
  }

  private int[] resolveMinority() {
    if (detectMinorityClass) {
      return findSmallestNonEmptyClass();
    }
    return new int[]{parseManualClassIndex(), Integer.MAX_VALUE};
  }

  private int[] findSmallestNonEmptyClass() {
    int[] classCounts = getInputFormat().attributeStats(getInputFormat().classIndex()).nominalCounts;
    int minIndex = 0;
    int min = Integer.MAX_VALUE;
    for (int i = 0; i < classCounts.length; i++) {
      if (classCounts[i] != 0 && classCounts[i] < min) {
        min = classCounts[i];
        minIndex = i;
      }
    }
    return new int[]{minIndex, min};
  }

  private int parseManualClassIndex() {
    String classVal = getClassValue();
    if (classVal.equalsIgnoreCase("first")) return 1;
    if (classVal.equalsIgnoreCase("last")) return getInputFormat().numClasses();
    int idx = Integer.parseInt(classVal);
    if (idx > getInputFormat().numClasses())
      throw new IllegalArgumentException("value index must be <= the number of classes");
    return idx - 1;
  }

  private int adjustNearestNeighbors(int minCount) {
    int adjusted = minCount <= nearestNeighbors ? minCount - 1 : nearestNeighbors;
    if (adjusted < 1)
      throw new IllegalStateException("Cannot use 0 neighbors!");
    return adjusted;
  }

  private Instances collectMinoritySample(int minIndex) {
    Instances sample = getInputFormat().stringFreeStructure();
    Enumeration instanceEnum = getInputFormat().enumerateInstances();
    while (instanceEnum.hasMoreElements()) {
      Instance instance = (Instance) instanceEnum.nextElement();
      push((Instance) instance.copy());
      if ((int) instance.classValue() == minIndex) {
        sample.add(instance);
      }
    }
    return sample;
  }

  private Map buildVdmMap() {
    Map vdmMap = new HashMap();
    Enumeration attrEnum = getInputFormat().enumerateAttributes();
    while (attrEnum.hasMoreElements()) {
      Attribute attr = (Attribute) attrEnum.nextElement();
      if (!attr.equals(getInputFormat().classAttribute()) && (attr.isNominal() || attr.isString())) {
        vdmMap.put(attr, computeVdm(attr));
      }
    }
    return vdmMap;
  }

  private double[][] computeVdm(Attribute attr) {
    double[][] vdm = new double[attr.numValues()][attr.numValues()];
    int[] featureValueCounts = new int[attr.numValues()];
    int[][] featureValueCountsByClass =
        new int[getInputFormat().classAttribute().numValues()][attr.numValues()];
    Enumeration instanceEnum = getInputFormat().enumerateInstances();
    while (instanceEnum.hasMoreElements()) {
      Instance instance = (Instance) instanceEnum.nextElement();
      int value = (int) instance.value(attr);
      featureValueCounts[value]++;
      featureValueCountsByClass[(int) instance.classValue()][value]++;
    }
    for (int v1 = 0; v1 < attr.numValues(); v1++) {
      for (int v2 = 0; v2 < attr.numValues(); v2++) {
        vdm[v1][v2] = computeVdmCell(v1, v2, featureValueCounts, featureValueCountsByClass);
      }
    }
    return vdm;
  }

  private double computeVdmCell(int v1, int v2, int[] fvc, int[][] fvcByClass) {
    double sum = 0;
    for (int c = 0; c < getInputFormat().numClasses(); c++) {
      sum += Math.abs(fvcByClass[c][v1] / (double) fvc[v1] - fvcByClass[c][v2] / (double) fvc[v2]);
    }
    return sum;
  }

  private Set buildExtraIndexSet(Instances sample, Random rand) {
    List extraIndices = new ArrayList();
    double remainder = (getPercentage() / 100) - Math.floor(getPercentage() / 100.0);
    int extraCount = (int) (remainder * sample.numInstances());
    if (extraCount >= 1) {
      for (int i = 0; i < sample.numInstances(); i++) {
        extraIndices.add(i);
      }
    }
    Collections.shuffle(extraIndices, rand);
    extraIndices = extraIndices.subList(0, extraCount);
    return new HashSet(extraIndices);
  }

  private void synthesize(Instances sample, Map vdmMap, int knn,
                           int minIndex, Random rand, Set extraIndexSet) {
    Instance[] nnArray = new Instance[knn];
    for (int i = 0; i < sample.numInstances(); i++) {
      Instance instanceI = sample.instance(i);
      findNearestNeighbors(sample, i, instanceI, vdmMap, knn, nnArray);
      int n = (int) Math.floor(getPercentage() / 100);
      while (n > 0 || extraIndexSet.remove(i)) {
        push(createSyntheticInstance(instanceI, nnArray, knn, rand, sample, minIndex));
        n--;
      }
    }
  }

  private void findNearestNeighbors(Instances sample, int skipIndex, Instance instanceI,
                                     Map vdmMap, int k, Instance[] nnArray) {
    List distanceToInstance = new ArrayList();
    for (int j = 0; j < sample.numInstances(); j++) {
      if (j != skipIndex) {
        double distance = computeDistance(instanceI, sample.instance(j), vdmMap);
        distanceToInstance.add(new Object[]{distance, sample.instance(j)});
      }
    }
    distanceToInstance.sort((o1, o2) ->
        Double.compare((Double) ((Object[]) o1)[0], (Double) ((Object[]) o2)[0]));
    for (int j = 0; j < k; j++) {
      nnArray[j] = (Instance) ((Object[]) distanceToInstance.get(j))[1];
    }
  }

  private double computeDistance(Instance instanceI, Instance instanceJ, Map vdmMap) {
    double distance = 0;
    Enumeration attrEnum = getInputFormat().enumerateAttributes();
    while (attrEnum.hasMoreElements()) {
      Attribute attr = (Attribute) attrEnum.nextElement();
      if (!attr.equals(getInputFormat().classAttribute())) {
        double iVal = instanceI.value(attr);
        double jVal = instanceJ.value(attr);
        if (attr.isNumeric()) {
          distance += Math.pow(iVal - jVal, 2);
        } else {
          distance += ((double[][]) vdmMap.get(attr))[(int) iVal][(int) jVal];
        }
      }
    }
    return Math.pow(distance, .5);
  }

  private DenseInstance createSyntheticInstance(Instance instanceI, Instance[] nnArray,
                                                  int knn, Random rand,
                                                  Instances sample, int minIndex) {
    double[] values = new double[sample.numAttributes()];
    int nn = rand.nextInt(knn);
    Enumeration attrEnum = getInputFormat().enumerateAttributes();
    while (attrEnum.hasMoreElements()) {
      Attribute attr = (Attribute) attrEnum.nextElement();
      if (!attr.equals(getInputFormat().classAttribute())) {
        values[attr.index()] = computeAttributeValue(instanceI, nnArray[nn], nnArray, knn, attr, rand);
      }
    }
    values[sample.classIndex()] = minIndex;
    return new DenseInstance(1.0, values);
  }

  private double computeAttributeValue(Instance instanceI, Instance nn, Instance[] nnArray,
                                        int knn, Attribute attr, Random rand) {
    if (attr.isNumeric()) {
      return instanceI.value(attr) + rand.nextDouble() * (nn.value(attr) - instanceI.value(attr));
    }
    if (attr.isDate()) {
      double interpolated = instanceI.value(attr) + rand.nextDouble() * (nn.value(attr) - instanceI.value(attr));
      return (long) interpolated;
    }
    return computeNominalValue(instanceI, nnArray, knn, attr);
  }

  private double computeNominalValue(Instance instanceI, Instance[] nnArray, int knn, Attribute attr) {
    int[] valueCounts = new int[attr.numValues()];
    valueCounts[(int) instanceI.value(attr)]++;
    for (int nnEx = 0; nnEx < knn; nnEx++) {
      valueCounts[(int) nnArray[nnEx].value(attr)]++;
    }
    int maxIndex = 0;
    int max = Integer.MIN_VALUE;
    for (int index = 0; index < attr.numValues(); index++) {
      if (valueCounts[index] > max) {
        max = valueCounts[index];
        maxIndex = index;
      }
    }
    return maxIndex;
  }

  public static void main(String[] args) {
    runFilter(new SmoteFilter(), args);
  }
}
