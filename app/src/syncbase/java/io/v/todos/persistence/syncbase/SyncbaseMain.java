// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;

import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;
import io.v.v23.VFutures;
import io.v.v23.syncbase.Collection;
import io.v.v23.verror.ExistException;
import io.v.v23.verror.VException;

public class SyncbaseMain extends SyncbasePersistence implements MainPersistence {
    public static final String MAIN_COLLECTION_NAME = "userdata";

    private static final Object sMainCollectionMutex = new Object();
    private static volatile Collection sMainCollection;

    public static boolean isInitialized() {
        return sMainCollection != null;
    }

    /**
     * This constructor blocks until the instance is ready for use.
     */
    public SyncbaseMain(Activity activity, ListEventListener<ListMetadata> listener)
            throws VException, SyncbaseServer.StartException {
        super(activity);

        synchronized (sMainCollectionMutex) {
            if (sMainCollection == null) {
                Collection mainCollection = getDatabase()
                        .getCollection(mVContext, MAIN_COLLECTION_NAME);
                try {
                    VFutures.sync(mainCollection.create(mVContext, null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sMainCollection = mainCollection;
            }
        }
    }

    @Override
    public void addTodoList(ListSpec listSpec) {

    }

    @Override
    public void deleteTodoList(String key) {

    }

    @Override
    public void completeAllTasks(ListMetadata listMetadata) {

    }
}
