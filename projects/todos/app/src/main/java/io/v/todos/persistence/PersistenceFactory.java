// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import android.content.Context;

import io.v.todos.TodoList;

public final class PersistenceFactory {
    private PersistenceFactory(){}

    /**
     * Instantiates a persistence object that can be used to manipulate todo lists.
     *
     * @param context an Android context, usually from an Android activity or application
     */
    public static MainPersistence getMainPersistence(Context context,
                                                     ListEventListener<TodoList> listener) {
        // TODO(rosswang): Choose this by build variant.
        return new FirebaseMain(context, listener);
    }

    /**
     * Instantiates a persistence object that can be used to manipulate a todo list.
     *
     * @param context an Android context, usually from an Android activity or application
     */
    public static TodoListPersistence getTodoListPersistence(Context context, String key,
                                                             TodoListListener listener) {
        // TODO(rosswang): Choose this by build variant.
        return new FirebaseTodoList(context, key, listener);
    }
}
