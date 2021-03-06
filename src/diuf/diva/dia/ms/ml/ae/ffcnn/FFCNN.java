/*****************************************************
  N-light-N
  
  A Highly-Adaptable Java Library for Document Analysis with
  Convolutional Auto-Encoders and Related Architectures.
  
  -------------------
  Author:
  2016 by Mathias Seuret <mathias.seuret@unifr.ch>
      and Michele Alberti <michele.alberti@unifr.ch>
  -------------------

  This software is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation version 3.

  This software is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this software; if not, write to the Free Software
  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 ******************************************************************************/

package diuf.diva.dia.ms.ml.ae.ffcnn;

import diuf.diva.dia.ms.ml.Classifier;
import diuf.diva.dia.ms.ml.ae.scae.Convolution;
import diuf.diva.dia.ms.ml.ae.scae.SCAE;
import diuf.diva.dia.ms.util.DataBlock;

import java.io.*;
import java.util.ArrayList;

/**
 * This is a feed forward convolutional network built out of an SCAE.
 * @author Mathias Seuret,Michele Alberti
 */
public class FFCNN implements Classifier, Serializable, Cloneable {

    private static final long serialVersionUID = -7639910899015336328L;
    /**
     * Width of the perception patch
     */
    private final int inputWidth;

    /**
     * Height of the perception patch
     */
    private final int inputHeight;

    /**
     * Depth of the perception patch
     */
    private final int inputDepth;

    /**
     * List of layers.
     */
    private ArrayList<ConvolutionLayer> layers = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creates an FFCNN out of a SCAE. No further layer will be added.
     * @param scae scae to use as model
     */
    public FFCNN(final SCAE scae) {

        // Get the input parameters from SCAE
        inputWidth = scae.getInputPatchWidth();
        inputHeight = scae.getInputPatchHeight();
        inputDepth = scae.getInputPatchDepth();

        // Add all basic layers from the scae
        for (Convolution c : scae.getLayers()) {
            layers.add(new ConvolutionLayer(c));
        }

        // Setting input and previous error for all layers
        for (int i = 1; i < layers.size(); i++) {
            layers.get(i).setInput(layers.get(i - 1).getOutput(), 0, 0);
            layers.get(i).setPrevError(layers.get(i - 1).getError());
        }
    }

    /**
     * Constructs an FFCNN out of an SCAE and a set of several neural layers.
     * @param base scae to use as model
     * @param layerClassName name of the class with which should the classifying layers built with
     * @param nbClasses specifies how many classes should be classified by the FFCNN
     */
    public FFCNN(final SCAE base, String layerClassName, int nbClasses) {
        this(base, layerClassName, nbClasses, new int[0]);
    }

    /**
     * Creates an FFCNN. With this constructor it is possible to specify, and then add, additional
     * layers on top of the converted SCAE. The number of classes directly specifies how many neurons should
     * have the top layer.
     * @param base scae to use as model
     * @param layerClassName name of the class with which should the classifying layers built with
     * @param nbClasses specifies how many classes should be classified by the FFCNN
     * @param additionalLayers number of neurons in the additional classification layers
     */
    public FFCNN(final SCAE base, String layerClassName, final int nbClasses, int[] additionalLayers) {
        inputWidth = base.getInputPatchWidth();
        inputHeight = base.getInputPatchHeight();
        inputDepth = base.getInputPatchDepth();

        // Add all basic layers from the base
        for (Convolution c : base.getLayers()) {
            layers.add(new ConvolutionLayer(c));
        }

        // Add the additional layer required for classification
        for (int nbNeurons : additionalLayers) {
            ConvolutionLayer top = layers.get(layers.size()-1);
            layers.add(new ConvolutionLayer(top, layerClassName, nbNeurons));
        }

        // Add the top layer
        ConvolutionLayer top = layers.get(layers.size() - 1);
        layers.add(new ConvolutionLayer(top, layerClassName, nbClasses));

        // Adjust error datablocks rewferences
        for (int i = 1; i < layers.size(); i++) {
            layers.get(i).setInput(layers.get(i - 1).getOutput(), 0, 0);
            layers.get(i).setPrevError(layers.get(i - 1).getError());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Setting input
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Selects the input to use.
     *
     * @param db input data block
     * @param x  position
     * @param y  position
     */
    public void setInput(DataBlock db, int x, int y) {
        layers.get(0).setInput(db, x, y);
    }

    /**
     * Select the input to use - specify the center, not the corner.
     * @param db input data block
     * @param cx center x
     * @param cy center y
     */
    public void centerInput(DataBlock db, int cx, int cy) {
        //System.out.println("Setting center @ "+cx+","+cy);
        ConvolutionLayer l = layers.get(0);
        l.setInput(
                db,
                cx - l.getInputWidth() / 2,
                cy - l.getInputHeight() / 2
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Computing
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Computes the output.
     */
    public void compute() {
        for (ConvolutionLayer layer : layers) {
            layer.compute();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Getting the output/results
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This methods returns the classification result of the last evaluated input. The classification
     * might be single or multiclass. In case of single class the result will be a normal integer, in
     * case of the multiclass use the result will be an integer whose bits will represent the classes
     * which the output have been assigned to.
     * e.g. Single: res = 5 means output got classified as belonging to class 5
     * e.g. Multi: res = 5 (0..0101) means output got "classified"as belonging to the class three and one).
     *
     * @param multiClass defines whether or not the result will be multiclass
     * @return the index of the output with the highest value
     */
    public int getOutputClass(boolean multiClass) {
        int res = 0;
        if (multiClass) {
            DataBlock db = layers.get(layers.size() - 1).getOutput();
            for (int i = 0; i < db.getDepth(); i++) {
                if (db.getValue(i, 0, 0) > 0.35f) {
                    res |= (0x01 << i);
                }
            }
        } else {
            DataBlock db = layers.get(layers.size() - 1).getOutput();
            for (int i = 1; i < db.getDepth(); i++) {
                if (db.getValue(i, 0, 0) > db.getValue(res, 0, 0)) {
                    res = i;
                }
            }

        }
        return res;
    }

    // TODO REMOVE this is for debugging only
    public void getOutputScores() {

        int res = 0;
        DataBlock db = layers.get(layers.size() - 1).getOutput();

        StringBuilder s = new StringBuilder();
        s.append("[");
        for (int i = 0; i < db.getDepth(); i++) {
            if (db.getValue(i, 0, 0) > db.getValue(res, 0, 0)) {
                res = i;
            }
            s.append(Math.round(100 * db.getValue(i, 0, 0)) / 100.0);
            s.append(",");
        }
        s.append("] \t\t");
        s.append(Math.round(100 * db.getValue(res, 0, 0)) / 100.0);
        System.out.println(s);
    }

    /**
     * Returns the size of the output. Used mainly for knowing how many maximal different classes are we
     * working with
     *
     * @return the size of the output
     */
    public int getOutputSize() {
        return layers.get(layers.size() - 1).getOutput().getDepth();
    }

    /**
     * @return the output data block
     */
    public DataBlock getOutput() {
        return layers.get(layers.size() - 1).getOutput();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Learning
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Indicates what was expected for a given output.
     *
     * @param expectedClass output number which should correspond to the class
     * @param expectedValue expected value for the expected class
     */
    public void setExpected(int expectedClass, float expectedValue) {
        topLayer().setExpected(0, 0, expectedClass, expectedValue);
    }

    /**
     * Learn all layers
     */
    public float learn() {
        learn(layers.size());
        // TODO temporary as maybe learn() will return void
        return -1;
    }

    /**
     * Learn the specified amount of layers from the top
     *
     * @param nbLayers how many layers from the top ?
     */
    public float learn(int nbLayers) {
        for (int i = layers.size() - 1; i>=0 && i >= layers.size() - nbLayers; i--) {
            layers.get(i).learn();
        }
        // TODO temporary as maybe learn() will return void
        return -1;
    }

    /**
     * Backpropagate all layers
     *
     * @return average of the absolute errors of each output of the top layer
     */
    public float backPropagate() {
        float res = backPropagate(layers.size());
        // Clear error in whole network
        for (ConvolutionLayer l : layers) {
            l.clearError();
        }
        return res;
    }

    /**
     * Backpropagate the specified amount of layers from the top
     *
     * @param nbLayers how many layers from the top ?
     * @return average of the absolute errors of each output of the top layer
     */
    public float backPropagate(int nbLayers) {

        /* Backpropagate
         * The only reason the top layer is not in the for-loop is because we want to return
         * his error alone.
         */
        float err = layers.get(layers.size() - 1).backPropagate();
        for (int i = layers.size() - 2; i >= 0 && i >= layers.size() - nbLayers; i--) {
            layers.get(i).backPropagate();
        }

        // Clear error in whole network
        for (ConvolutionLayer l : layers) {
            l.clearError();
        }
        return err;
    }

    /**
     * Adds an error to a given output.
     * @param z output number
     * @param e error to add
     */
    public void addError(int z, float e) {
        layers.get(layers.size()-1).addError(0, 0, z, e);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Getters&Setters
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @return the width of the perception patch
     */
    public int getInputWidth() {
        return inputWidth;
    }

    /**
     * @return the height of the perception patch
     */
    public int getInputHeight() {
        return inputHeight;
    }

    /**
     * @return the depth of the perception patch
     */
    public int getInputDepth() {
        return inputDepth;
    }

    public ConvolutionLayer getLayer(int n) {
        return layers.get(n);
    }

    public int getOutputDepth() {
        return layers.get(layers.size() - 1).getOutput().getDepth();
    }

    /**
     * @return the number of layers in the network
     */
    public int countLayers() {
        return layers.size();
    }

    /**
     * @return the error of the base layer
     */
    public DataBlock getAccumulator() {
        return layers.get(0).prevAccumulator;
    }

    /**
     * Sets the learning speed of all layers. Default value: 1e-3f.
     * @param speed new learning speed
     */
    public void setLearningSpeed(float speed) {
        for (int i = 0; i < countLayers(); i++) {
            setLearningSpeed(i, speed);
        }
    }

    /**
     * Sets the learning speed of a given layer. Default value: 1e-3f.
     * @param layerNumber layer number
     * @param speed new speed
     */
    public void setLearningSpeed(int layerNumber, float speed) {
        layers.get(layerNumber).setLearningSpeed(speed);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private ConvolutionLayer topLayer() {
        return layers.get(layers.size() - 1);
    }

    /**
     * Must return a string indicating the name of the classifier.
     * Useful to avoid using "instanceof"
     *
     * @return the name of the classifier as string
     */
    public String name() {
        return "FFCNN";
    }

    /**
     * Returns a string indicating the type of classifier.
     *
     * @return the type of the classifier as string
     */
    @Override
    public String type() {
        return "pixel";
    }

    /**
     * Useful to select how many layer we want to train from the top
     *
     * @return the number of layers
     */
    public int getNumLayers() {
        return layers.size();
    }

    public void evaluateInputImportance() {
        ConvolutionLayer top = layers.get(layers.size() - 1);
        for (int i=0; i<top.outDepth; i++) {
            top.getOutput().setValue(i, 0, 0, 1);
        }
        for (int l=layers.size()-1; l>=0; l--) {
            layers.get(l).getInput().clear();
            layers.get(l).evaluateInputImportance();
        }
    }

    public void evaluateInputImportance(int inputNum) {
        ConvolutionLayer top = layers.get(layers.size() - 1);
        for (int i=0; i<top.outDepth; i++) {
            top.getOutput().setValue(i, 0, 0, (i == inputNum) ? 1 : 0);
        }
        for (int l=layers.size()-1; l>=0; l--) {
            layers.get(l).getInput().clear();
            layers.get(l).evaluateInputImportance();
        }
    }

    /**
     * Saves the network to a file.
     *
     * @param fileName file name
     * @throws IOException if the file cannot be written to
     */
    public void save(String fileName) throws IOException {
        // Check whether the path is existing, if not create it
        File file = new File(fileName);
        if (!file.isDirectory()) {
            file = file.getParentFile();
        }
        if (!file.exists()) {
            file.mkdirs();
        }

        ObjectOutputStream oop = new ObjectOutputStream(new FileOutputStream(fileName));
        // Dummy input
        setInput(new DataBlock(getInputWidth(), getInputHeight(), getInputDepth()), 0, 0);
        oop.writeObject(this);
        oop.close();
    }

    /**
     * Loads the network from a file
     *
     * @param fileName file name
     * @return a new instance
     * @throws IOException            if the file cannot be read for some reason
     * @throws ClassNotFoundException if the file contains an older version of the FFCNN
     */
    public FFCNN load(String fileName) throws IOException, ClassNotFoundException {
        return (FFCNN) Classifier.load(fileName);
    }

    /**
     * Clones the FFCNN. Throws an error in case of failure.
     * @return a new FFCNN
     */
    @Override
    public FFCNN clone() throws CloneNotSupportedException {
        super.clone();
        FFCNN res;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);

            res = (FFCNN) ois.readObject();
        } catch (Exception e) {
            throw new Error("Could not clone the FFCNN");
        }
        return res;
    }


}