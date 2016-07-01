// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.UUID;

import io.v.syncbase.Collection;
import io.v.syncbase.DatabaseHandle;
import io.v.syncbase.Id;
import io.v.syncbase.Syncbase;
import io.v.syncbase.core.VError;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;

public class SyncbaseMain extends SyncbasePersistence implements MainPersistence {
    public SyncbaseMain(Activity activity, Bundle savedInstanceState,
                        ListEventListener<ListMetadata> listener) {
        super(activity, savedInstanceState);


        // Fire the listener for existing list metadata.
        for (Id listId : sListMetadataTrackerMap.keySet()) {
            ListMetadataTracker tracker = sListMetadataTrackerMap.get(listId);
            tracker.fireListener(listener);
        }

        // Register the listener for future updates.
        setMainListener(listener);
    }

    @Override
    public String addTodoList(ListSpec listSpec) {
        DatabaseHandle.CollectionOptions opts = new DatabaseHandle.CollectionOptions();
        try {
            // TODO(alexfandrianto): We're not allowed to have dashes in our collection names still!
            // You also must start with a letter, not a number.
            Collection c = sDb.collection("list_" + UUID.randomUUID().toString().replaceAll("-", ""), opts);
            c.put(TODO_LIST_KEY, listSpec);
            return c.getId().encode();
        } catch (VError vError) {
            Log.e(TAG, "Failed to create todo list collection", vError);
            throw new RuntimeException(vError);
        }
    }

    @Override
    public void deleteTodoList(String key) {
        Id listId = Id.decode(key);
        Collection c = sDb.getCollection(listId);
        try {
            c.delete(TODO_LIST_KEY);
        } catch (VError vError) {
            Log.e(TAG, "Failed to delete todo list key", vError);
        }
        // TODO(alexfandrianto): Instead of deleting the key, we should destroy the collection.
        // Unfortunately, I can't yet: https://v.io/i/1374
    }

    @Override
    public void close() {
        removeMainListener();
        if (isInitialized()) {
            Syncbase.shutdown();
        }
    }
}
