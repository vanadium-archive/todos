// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

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
    private static final String SHOW_DONE_KEY = "ShowDone";

    private final Firebase mTodoList, mTasks;
    private final ValueEventListener mTodoListListener;
    private final ChildEventListener mTasksListener;
    private final SharedPreferences mSharedPreferences;

    private ListSpec mListSpec;

    public FirebaseTodoList(Context context, String todoListKey, final TodoListListener listener) {
        super(context);

        mTodoList = getFirebase().child(FirebaseMain.TODO_LISTS).child(todoListKey);
        mTasks = getFirebase().child(TASKS).child(todoListKey);

        // Listen and forward changes to the ListSpec metadata.
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

        // Listen and forward changes to task items in this list.
        mTasksListener = mTasks.addChildEventListener(new TaskChildEventListener(listener));

        // Listen and forward changes to the show done toggle.
        // TODO(alexfandrianto): This setting is currently shared across all todo lists, but it is
        // also valid to make the setting apply to a specific todo list. We have not decided yet.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        listener.onUpdateShowDone(mSharedPreferences.getBoolean(SHOW_DONE_KEY, true));
        mSharedPreferences.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                if (s.equals(SHOW_DONE_KEY)) {
                    listener.onUpdateShowDone(sharedPreferences.getBoolean(s, true));
                }
            }
        });
    }

    @Override
    public void updateTodoList(ListSpec listSpec) {
        mTodoList.setValue(listSpec);
    }

    @Override
    public void deleteTodoList() {
        mTodoList.removeValue();
    }

    @Override
    public void shareTodoList(Iterable<String> emails) {
        // Not implemented.
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
    public void setShowDone(boolean showDone) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(SHOW_DONE_KEY, showDone);
        editor.apply();
    }

    @Override
    public void close() {
        getFirebase().removeEventListener(mTodoListListener);
        getFirebase().removeEventListener(mTasksListener);
    }
}
