// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;

import java.util.UUID;

import io.v.syncbase.Collection;
import io.v.syncbase.DatabaseHandle;
import io.v.syncbase.Id;
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
        // TODO(alexfandrianto): We do want to create this with a syncgroup, but even if we set
        // the flag to off, it takes too long to create (and put) on the UI thread. To work around
        // this, we might mock the encoded Id synchronously and then do creation/put asynchronously.
        DatabaseHandle.CollectionOptions opts = new DatabaseHandle.CollectionOptions();
        opts.withoutSyncgroup = true;
        Collection c = mDb.collection(UUID.randomUUID().toString(), opts);
        c.put(TODO_LIST_KEY, listSpec);
        return c.getId().encode();
    }

    @Override
    public void deleteTodoList(String key) {
        Id listId = Id.decode(key);
        Collection c = mDb.getCollection(listId);
        c.delete(TODO_LIST_KEY);
        // TODO(alexfandrianto): Instead of deleting the key, should we destroy the collection?
    }

    @Override
    public void close() {
        removeMainListener();
        super.close();
    }
}
