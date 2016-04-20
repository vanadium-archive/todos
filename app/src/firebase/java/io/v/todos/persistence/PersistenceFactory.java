// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import android.content.Context;

import io.v.todos.model.TodoList;
import io.v.todos.persistence.firebase.FirebaseMain;
import io.v.todos.persistence.firebase.FirebaseTodoList;

public final class PersistenceFactory {
    private PersistenceFactory(){}

    /**
     * Instantiates a persistence object that can be used to manipulate todo lists.
     *
     * @param context an Android context, usually from an Android activity or application
     */
    public static MainPersistence getMainPersistence(Context context,
                                                     ListEventListener<TodoList> listener) {
        return new FirebaseMain(context, listener);
    }

    /**
     * Instantiates a persistence object that can be used to manipulate a todo list.
     *
     * @param context an Android context, usually from an Android activity or application
     */
    public static TodoListPersistence getTodoListPersistence(Context context, String key,
                                                             TodoListListener listener) {
        return new FirebaseTodoList(context, key, listener);
    }
}
