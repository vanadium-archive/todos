// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import android.content.Context;

import com.firebase.client.ChildEventListener;
import com.firebase.client.Firebase;

import io.v.todos.TodoList;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;

public class FirebaseMain extends FirebasePersistence implements MainPersistence {
    public static final String TODO_LISTS = "snackoos (TodoList)";

    private final Firebase mTodoLists;
    private final ChildEventListener mTodoListsListener;

    public FirebaseMain(Context context, final ListEventListener<TodoList> listener) {
        super(context);

        mTodoLists = getFirebase().child(TODO_LISTS);

        mTodoListsListener = mTodoLists.addChildEventListener(
                new ChildEventListenerAdapter<>(TodoList.class, listener));
    }

    @Override
    public void addTodoList(TodoList todoList) {
        mTodoLists.push().setValue(todoList);
    }

    @Override
    public void deleteTodoList(String key) {
        mTodoLists.child(key).removeValue();
    }

    @Override
    public void close() {
        getFirebase().removeEventListener(mTodoListsListener);
    }
}
