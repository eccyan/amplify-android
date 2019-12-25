/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.testutils;

import androidx.annotation.NonNull;

import com.amplifyframework.core.Amplify;
import com.amplifyframework.core.ResultListener;
import com.amplifyframework.core.model.Model;
import com.amplifyframework.datastore.DataStoreException;
import com.amplifyframework.datastore.DataStoreItemChange;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A utility to facilitate synchronous calls to the Amplify DataStore category.
 * This is not appropriate for production use, but is valuable in test code.
 * Test code wants to perform a series of sequential verifications, with the assumption
 * that a DataStore operation has completed with some kind of terminal result.
 */
public final class SynchronousDataStore {
    private static SynchronousDataStore singleton;

    @SuppressWarnings("checkstyle:all") private SynchronousDataStore() {}

    /**
     * Gets a singleton instance of the SynchronousDataStore.
     * @return Singleton instance of Synchronous DataStore
     */
    @NonNull
    public static synchronized SynchronousDataStore singleton() {
        if (SynchronousDataStore.singleton == null) {
            SynchronousDataStore.singleton = new SynchronousDataStore();
        }
        return SynchronousDataStore.singleton;
    }

    /**
     * Saves an item into the DataStore.
     * @param item Item to save
     * @param <T> The type of item being saved
     */
    public <T extends Model> void save(@NonNull T item) {
        LatchedConsumer<DataStoreItemChange<T>> saveConsumer = LatchedConsumer.instance();
        ResultListener<DataStoreItemChange<T>, DataStoreException> resultListener =
            ResultListener.instance(saveConsumer, EmptyConsumer.of(DataStoreException.class));
        Amplify.DataStore.save(item, resultListener);
        saveConsumer.awaitValue();
    }

    /**
     * Search for an item in the DataStore by its class type and ID.
     * @param clazz Class of item being accessed
     * @param itemId Unique ID of the item being accessed
     * @param <T> The type of item being accessed
     * @return An item with the provided class and ID, if present in DataStore
     * @throws NoSuchElementException If there is no matching item in the DataStore
     */
    @NonNull
    public <T extends Model> T get(@NonNull Class<T> clazz, @NonNull String itemId) {
        LatchedConsumer<Iterator<T>> queryConsumer = LatchedConsumer.instance();
        ResultListener<Iterator<T>, DataStoreException> resultListener =
            ResultListener.instance(queryConsumer, EmptyConsumer.of(DataStoreException.class));
        Amplify.DataStore.query(clazz, resultListener);

        final Iterator<T> iterator = queryConsumer.awaitValue();
        while (iterator.hasNext()) {
            T value = iterator.next();
            if (value.getId().equals(itemId)) {
                return value;
            }
        }

        throw new NoSuchElementException("No item in DataStore with class = " + clazz + " and id = " + itemId);
    }
}
