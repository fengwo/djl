/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package ai.djl.training.dataset;

import ai.djl.Device;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;

/**
 * A {@code Batch} is used to hold multiple items (data and labels) from a {@link Dataset}.
 *
 * <p>When training and performing inference, it is often more efficient to run multiple items
 * through a network simultaneously rather than one at a time. For this reason, much of the API is
 * oriented around the {@code Batch} class.
 *
 * <p>In a {@code Batch}, data and label are each an {@link NDList}. The data {@link NDList}
 * represents the data for each input in the batch. The number of {@link ai.djl.ndarray.NDArray}s in
 * the NDList is based on the number of different kinds of inputs, not the batch size. Similarly,
 * the label {@link NDList} represents the labels for each output.
 *
 * <p>For example, an Image Question and Answer dataset has two inputs: an image and the question.
 * In this case, the data in the {@code Batch} will be an {@link NDList} containing a NCHW image
 * {@link ai.djl.ndarray.NDArray} and a NTC question {@link ai.djl.ndarray.NDArray}. The label will
 * be an {@link NDList} containing only a NTC answer {@link ai.djl.ndarray.NDArray}.
 *
 * <p>In order to differentiate a batch vs a single record (despite them both consisting of two
 * {@link NDList}s), we have the {@link Batch} and the {@link Record} respectively.
 */
public class Batch implements AutoCloseable {

    private NDManager manager;
    private NDList data;
    private NDList labels;
    private Batchifier batchifier;

    /**
     * Creates a new instance of {@code Batch} with the given manager, data and labels.
     *
     * @param manager the {@link NDManager} to be attached to data and labels
     * @param data the {@link NDList} containing the data
     * @param labels the {@link NDList} containing the labels
     */
    public Batch(NDManager manager, NDList data, NDList labels) {
        this.manager = manager;
        data.attach(manager);
        labels.attach(manager);
        this.data = data;
        this.labels = labels;
    }

    /**
     * Creates a new instance of {@code Batch} with the given manager, data and labels.
     *
     * @param manager the {@link NDManager} to be attached to data and labels
     * @param data the {@link NDList} containing the data
     * @param labels the {@link NDList} containing the labels
     * @param batchifier the {@link Batchifier} that is used for split
     */
    public Batch(NDManager manager, NDList data, NDList labels, Batchifier batchifier) {
        this.manager = manager;
        data.attach(manager);
        labels.attach(manager);
        this.data = data;
        this.labels = labels;
        this.batchifier = batchifier;
    }

    /**
     * Gets the {@link NDManager} that is attached to this {@code Batch}.
     *
     * @return the {@link NDManager} attached to this {@code Batch}
     */
    public NDManager getManager() {
        return manager;
    }

    /**
     * Gets the data of this {@code Batch}.
     *
     * @return an {@link NDList} that contains the data
     */
    public NDList getData() {
        return data;
    }

    /**
     * Gets the labels corresponding to the data of this {@code Batch}.
     *
     * @return an {@link NDList} that contains the labels
     */
    public NDList getLabels() {
        return labels;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        manager.close();
        manager = null;
    }

    /**
     * Splits the data and labels in the {@code Batch} across the given devices.
     *
     * <p>if {@code evenSplit} is {@code false}, that last device may have a smaller batch than the
     * rest.
     *
     * @param devices an array of {@link Device} across which the data must be split
     * @param evenSplit whether each slice must have the same shape
     * @return an array of {@code Batch}, each of which corresponds to a {@link Device}
     */
    public Batch[] split(Device[] devices, boolean evenSplit) {
        int size = devices.length;
        if (size == 1) {
            // TODO: we should change to following once we support slice:
            // NDList d = data.asInDevice(devices[0], false);
            // avoid copy if data already in device
            if (data.head().getDevice().equals(devices[0])) {
                return new Batch[] {new Batch(manager, data, labels, batchifier)};
            } else {
                NDList d = data.asInDevice(devices[0], true);
                NDList l = labels.asInDevice(devices[0], true);
                return new Batch[] {new Batch(manager, d, l, batchifier)};
            }
        }

        NDList[] splittedData = split(data, size, evenSplit);
        NDList[] splittedLabels = split(labels, size, evenSplit);

        Batch[] splitted = new Batch[splittedData.length];
        for (int i = 0; i < splittedData.length; ++i) {
            NDList d = splittedData[i].asInDevice(devices[i], true);
            NDList l = splittedLabels[i].asInDevice(devices[i], true);
            splitted[i] = new Batch(manager, d, l, batchifier);
        }
        return splitted;
    }

    private NDList[] split(NDList list, int numOfSlices, boolean evenSplit) {
        if (batchifier == null) {
            throw new IllegalStateException(
                    "Split can only be called on a batch containing a batchifier");
        }
        return batchifier.split(list, numOfSlices, evenSplit);
    }
}
