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
 * The doSMOTE() method — which contains the entire algorithm — is identical
 * to the original.
 */

package com.isw2project.classifier;

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

import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class SmoteFilter
  extends Filter
  implements SupervisedFilter, OptionHandler, TechnicalInformationHandler {

  static final long serialVersionUID = -1653880819059250364L;

  protected int m_NearestNeighbors = 5;
  protected int m_RandomSeed = 1;
  protected double m_Percentage = 100.0;
  protected String m_ClassValueIndex = "0";
  protected boolean m_DetectMinorityClass = true;

  public String globalInfo() {
    return "Resamples a dataset by applying the Synthetic Minority Oversampling TEchnique (SMOTE)."
        + " The original dataset must fit entirely in memory."
        + " The amount of SMOTE and number of nearest neighbors may be specified."
        + " For more information, see \n\n"
        + getTechnicalInformation().toString();
  }

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

  public String getRevision() {
    return RevisionUtils.extract("$Revision: 8108 $");
  }

  public Capabilities getCapabilities() {
    Capabilities result = super.getCapabilities();
    result.disableAll();
    result.enableAllAttributes();
    result.enable(Capability.MISSING_VALUES);
    result.enable(Capability.NOMINAL_CLASS);
    result.enable(Capability.MISSING_CLASS_VALUES);
    return result;
  }

  public Enumeration listOptions() {
    Vector newVector = new Vector();
    newVector.addElement(new Option(
        "\tSpecifies the random number seed\n\t(default 1)",
        "S", 1, "-S <num>"));
    newVector.addElement(new Option(
        "\tSpecifies percentage of SMOTE instances to create.\n\t(default 100.0)\n",
        "P", 1, "-P <percentage>"));
    newVector.addElement(new Option(
        "\tSpecifies the number of nearest neighbors to use.\n\t(default 5)\n",
        "K", 1, "-K <nearest-neighbors>"));
    newVector.addElement(new Option(
        "\tSpecifies the index of the nominal class value to SMOTE\n\t(default 0: auto-detect non-empty minority class))\n",
        "C", 1, "-C <value-index>"));
    return newVector.elements();
  }

  public void setOptions(String[] options) throws Exception {
    String seedStr = Utils.getOption('S', options);
    setRandomSeed(seedStr.length() != 0 ? Integer.parseInt(seedStr) : 1);

    String percentageStr = Utils.getOption('P', options);
    setPercentage(percentageStr.length() != 0 ? new Double(percentageStr).doubleValue() : 100.0);

    String nnStr = Utils.getOption('K', options);
    setNearestNeighbors(nnStr.length() != 0 ? Integer.parseInt(nnStr) : 5);

    String classValueIndexStr = Utils.getOption('C', options);
    if (classValueIndexStr.length() != 0) {
      setClassValue(classValueIndexStr);
    } else {
      m_DetectMinorityClass = true;
    }
  }

  public String[] getOptions() {
    Vector<String> result = new Vector<>();
    result.add("-C"); result.add(getClassValue());
    result.add("-K"); result.add("" + getNearestNeighbors());
    result.add("-P"); result.add("" + getPercentage());
    result.add("-S"); result.add("" + getRandomSeed());
    return result.toArray(new String[0]);
  }

  public String randomSeedTipText()      { return "The seed used for random sampling."; }
  public int    getRandomSeed()          { return m_RandomSeed; }
  public void   setRandomSeed(int value) { m_RandomSeed = value; }

  public String percentageTipText() { return "The percentage of SMOTE instances to create."; }
  public double getPercentage()     { return m_Percentage; }
  public void   setPercentage(double value) {
    if (value >= 0) m_Percentage = value;
    else System.err.println("Percentage must be >= 0!");
  }

  public String nearestNeighborsTipText()      { return "The number of nearest neighbors to use."; }
  public int    getNearestNeighbors()           { return m_NearestNeighbors; }
  public void   setNearestNeighbors(int value) {
    if (value >= 1) m_NearestNeighbors = value;
    else System.err.println("At least 1 neighbor necessary!");
  }

  public String classValueTipText() {
    return "The index of the class value to which SMOTE should be applied. "
        + "Use a value of 0 to auto-detect the non-empty minority class.";
  }
  public String getClassValue() { return m_ClassValueIndex; }
  public void   setClassValue(String value) {
    m_ClassValueIndex = value;
    m_DetectMinorityClass = m_ClassValueIndex.equals("0");
  }

  public boolean setInputFormat(Instances instanceInfo) throws Exception {
    super.setInputFormat(instanceInfo);
    super.setOutputFormat(instanceInfo);
    return true;
  }

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

  protected void doSMOTE() throws Exception {
    int minIndex = 0;
    int min = Integer.MAX_VALUE;
    if (m_DetectMinorityClass) {
      int[] classCounts = getInputFormat().attributeStats(getInputFormat().classIndex()).nominalCounts;
      for (int i = 0; i < classCounts.length; i++) {
        if (classCounts[i] != 0 && classCounts[i] < min) {
          min = classCounts[i];
          minIndex = i;
        }
      }
    } else {
      String classVal = getClassValue();
      if (classVal.equalsIgnoreCase("first")) {
        minIndex = 1;
      } else if (classVal.equalsIgnoreCase("last")) {
        minIndex = getInputFormat().numClasses();
      } else {
        minIndex = Integer.parseInt(classVal);
      }
      if (minIndex > getInputFormat().numClasses())
        throw new Exception("value index must be <= the number of classes");
      minIndex--;
    }

    int nearestNeighbors;
    if (min <= getNearestNeighbors()) {
      nearestNeighbors = min - 1;
    } else {
      nearestNeighbors = getNearestNeighbors();
    }
    if (nearestNeighbors < 1)
      throw new Exception("Cannot use 0 neighbors!");

    Instances sample = getInputFormat().stringFreeStructure();
    Enumeration instanceEnum = getInputFormat().enumerateInstances();
    while (instanceEnum.hasMoreElements()) {
      Instance instance = (Instance) instanceEnum.nextElement();
      push((Instance) instance.copy());
      if ((int) instance.classValue() == minIndex)
        sample.add(instance);
    }

    Map vdmMap = new HashMap();
    Enumeration attrEnum = getInputFormat().enumerateAttributes();
    while (attrEnum.hasMoreElements()) {
      Attribute attr = (Attribute) attrEnum.nextElement();
      if (!attr.equals(getInputFormat().classAttribute())) {
        if (attr.isNominal() || attr.isString()) {
          double[][] vdm = new double[attr.numValues()][attr.numValues()];
          vdmMap.put(attr, vdm);
          int[] featureValueCounts = new int[attr.numValues()];
          int[][] featureValueCountsByClass = new int[getInputFormat().classAttribute().numValues()][attr.numValues()];
          instanceEnum = getInputFormat().enumerateInstances();
          while (instanceEnum.hasMoreElements()) {
            Instance instance = (Instance) instanceEnum.nextElement();
            int value = (int) instance.value(attr);
            int classValue = (int) instance.classValue();
            featureValueCounts[value]++;
            featureValueCountsByClass[classValue][value]++;
          }
          for (int v1 = 0; v1 < attr.numValues(); v1++) {
            for (int v2 = 0; v2 < attr.numValues(); v2++) {
              double sum = 0;
              for (int c = 0; c < getInputFormat().numClasses(); c++) {
                double c1i = featureValueCountsByClass[c][v1];
                double c2i = featureValueCountsByClass[c][v2];
                double c1  = featureValueCounts[v1];
                double c2  = featureValueCounts[v2];
                sum += Math.abs(c1i / c1 - c2i / c2);
              }
              vdm[v1][v2] = sum;
            }
          }
        }
      }
    }

    Random rand = new Random(getRandomSeed());

    List extraIndices = new LinkedList();
    double percentageRemainder = (getPercentage() / 100) - Math.floor(getPercentage() / 100.0);
    int extraIndicesCount = (int) (percentageRemainder * sample.numInstances());
    if (extraIndicesCount >= 1) {
      for (int i = 0; i < sample.numInstances(); i++)
        extraIndices.add(i);
    }
    Collections.shuffle(extraIndices, rand);
    extraIndices = extraIndices.subList(0, extraIndicesCount);
    Set extraIndexSet = new HashSet(extraIndices);

    Instance[] nnArray = new Instance[nearestNeighbors];
    for (int i = 0; i < sample.numInstances(); i++) {
      Instance instanceI = sample.instance(i);

      List distanceToInstance = new LinkedList();
      for (int j = 0; j < sample.numInstances(); j++) {
        Instance instanceJ = sample.instance(j);
        if (i != j) {
          double distance = 0;
          attrEnum = getInputFormat().enumerateAttributes();
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
          distance = Math.pow(distance, .5);
          distanceToInstance.add(new Object[]{distance, instanceJ});
        }
      }

      Collections.sort(distanceToInstance, new Comparator() {
        public int compare(Object o1, Object o2) {
          double d1 = (Double) ((Object[]) o1)[0];
          double d2 = (Double) ((Object[]) o2)[0];
          return Double.compare(d1, d2);
        }
      });

      Iterator entryIterator = distanceToInstance.iterator();
      int j = 0;
      while (entryIterator.hasNext() && j < nearestNeighbors) {
        nnArray[j] = (Instance) ((Object[]) entryIterator.next())[1];
        j++;
      }

      int n = (int) Math.floor(getPercentage() / 100);
      while (n > 0 || extraIndexSet.remove(i)) {
        double[] values = new double[sample.numAttributes()];
        int nn = rand.nextInt(nearestNeighbors);
        attrEnum = getInputFormat().enumerateAttributes();
        while (attrEnum.hasMoreElements()) {
          Attribute attr = (Attribute) attrEnum.nextElement();
          if (!attr.equals(getInputFormat().classAttribute())) {
            if (attr.isNumeric()) {
              double dif = nnArray[nn].value(attr) - instanceI.value(attr);
              double gap = rand.nextDouble();
              values[attr.index()] = instanceI.value(attr) + gap * dif;
            } else if (attr.isDate()) {
              double dif = nnArray[nn].value(attr) - instanceI.value(attr);
              double gap = rand.nextDouble();
              values[attr.index()] = (long) (instanceI.value(attr) + gap * dif);
            } else {
              int[] valueCounts = new int[attr.numValues()];
              valueCounts[(int) instanceI.value(attr)]++;
              for (int nnEx = 0; nnEx < nearestNeighbors; nnEx++)
                valueCounts[(int) nnArray[nnEx].value(attr)]++;
              int maxIndex = 0;
              int max = Integer.MIN_VALUE;
              for (int index = 0; index < attr.numValues(); index++) {
                if (valueCounts[index] > max) {
                  max = valueCounts[index];
                  maxIndex = index;
                }
              }
              values[attr.index()] = maxIndex;
            }
          }
        }
        values[sample.classIndex()] = minIndex;
        push(new DenseInstance(1.0, values));
        n--;
      }
    }
  }

  public static void main(String[] args) {
    runFilter(new SmoteFilter(), args);
  }
}
