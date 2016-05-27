// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;

import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;

public class SyncbaseTodoList extends SyncbasePersistence implements TodoListPersistence {
    public SyncbaseTodoList(Activity activity, Bundle savedInstanceState, String key,
                            TodoListListener listener) {
        super(activity, savedInstanceState);
    }

    @Override
    public void updateTodoList(ListSpec listSpec) {

    }

    @Override
    public void deleteTodoList() {

    }

    @Override
    public void completeTodoList() {

    }

    @Override
    public void addTask(TaskSpec task) {

    }

    @Override
    public void updateTask(Task task) {

    }

    @Override
    public void deleteTask(String key) {

    }

    @Override
    public void setShowDone(boolean showDone) {

    }
}
