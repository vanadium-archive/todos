// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import android.content.Context;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;

public class FirebaseTodoList extends FirebasePersistence implements TodoListPersistence {
    public static final String TASKS = "snackoo lists (Task)";

    private final Firebase mTodoList, mTasks;
    private final ValueEventListener mTodoListListener;
    private final ChildEventListener mTasksListener;

    private ListSpec mListSpec;

    public FirebaseTodoList(Context context, String todoListKey, final TodoListListener listener) {
        super(context);

        mTodoList = getFirebase().child(FirebaseMain.TODO_LISTS).child(todoListKey);
        mTasks = getFirebase().child(TASKS).child(todoListKey);

        mTodoListListener = mTodoList.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ListSpec listSpec = dataSnapshot.getValue(ListSpec.class);
                if (listSpec == null) {
                    listener.onDelete();
                } else {
                    mListSpec = listSpec;
                    listener.onUpdate(listSpec);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });

        mTasksListener = mTasks.addChildEventListener(new TaskChildEventListener(listener));
    }

    @Override
    public void updateTodoList(ListSpec listSpec) {
        mTodoList.setValue(listSpec);
    }

    @Override
    public void deleteTodoList() {
        mTodoList.removeValue();
    }

    private void updateListTimestamp() {
        mListSpec.setUpdatedAt(System.currentTimeMillis());
        mTodoList.setValue(mListSpec);
    }

    @Override
    public void addTask(TaskSpec task) {
        mTasks.push().setValue(task);
        updateListTimestamp();
    }

    @Override
    public void updateTask(Task task) {
        mTasks.child(task.key).setValue(task.toSpec());
        updateListTimestamp();
    }

    @Override
    public void deleteTask(String key) {
        mTasks.child(key).removeValue();
        updateListTimestamp();
    }

    @Override
    public void close() {
        getFirebase().removeEventListener(mTodoListListener);
        getFirebase().removeEventListener(mTasksListener);
    }
}
