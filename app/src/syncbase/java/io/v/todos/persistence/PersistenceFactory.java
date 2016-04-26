// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import android.app.Activity;

import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.model.ListMetadata;
import io.v.todos.persistence.syncbase.SyncbaseMain;
import io.v.todos.persistence.syncbase.SyncbaseTodoList;
import io.v.v23.verror.VException;

public final class PersistenceFactory {
    private PersistenceFactory(){}

    /**
     * Indicates whether {@link #getMainPersistence(Activity, ListEventListener)} may block. This
     * can affect whether a progress indicator is shown and whether a worker thread is used.
     */
    public static boolean mightGetMainPersistenceBlock() {
        return !SyncbaseMain.isInitialized();
    }

    /**
     * Instantiates a persistence object that can be used to manipulate todo lists.
     */
    public static MainPersistence getMainPersistence(
            Activity activity, ListEventListener<ListMetadata> listener)
            throws VException, SyncbaseServer.StartException {
        return new SyncbaseMain(activity, listener);
    }

    /**
     * Indicates whether {@link #getTodoListPersistence(Activity, String, TodoListListener)} may
     * block. This can affect whether a progress indicator is shown and whether a worker thread is
     * used.
     */
    public static boolean mightGetTodoListPersistenceBlock() {
        return !SyncbaseTodoList.isInitialized();
    }

    /**
     * Instantiates a persistence object that can be used to manipulate a todo list.
     */
    public static TodoListPersistence getTodoListPersistence(
            Activity activity, String key, TodoListListener listener)
            throws VException, SyncbaseServer.StartException {
        return new SyncbaseTodoList(activity, key, listener);
    }
}
