// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;

import java.util.Map;
import java.util.UUID;

import io.v.syncbase.BatchDatabase;
import io.v.syncbase.Collection;
import io.v.syncbase.Database;
import io.v.syncbase.Id;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;

public class SyncbaseTodoList extends SyncbasePersistence implements TodoListPersistence {
    private Collection mCollection;

    public SyncbaseTodoList(Activity activity, Bundle savedInstanceState, String key,
                            TodoListListener listener) {
        super(activity, savedInstanceState);

        Id listId = Id.decode(key);
        mCollection = mDb.getCollection(listId);

        // Fire the listener for existing data (list, tasks, show done status).
        ListSpec currentList = sListSpecMap.get(listId);
        if (currentList != null) {
            listener.onUpdate(currentList);
        }
        Map<String, TaskSpec> currentTasks = sTasksByListMap.get(listId);
        if (currentTasks != null) {
            for (String taskKey : currentTasks.keySet()) {
                listener.onItemAdd(new Task(taskKey, currentTasks.get(taskKey)));
            }
        }
        listener.onUpdateShowDone(sShowDone);

        // Register the listener for future updates.
        setTodoListListener(listener);
    }

    @Override
    public void updateTodoList(ListSpec listSpec) {
        mCollection.put(TODO_LIST_KEY, listSpec);
    }

    @Override
    public void deleteTodoList() {
        mCollection.delete(TODO_LIST_KEY);
    }

    @Override
    public void completeTodoList() {
        mDb.runInBatch(new Database.BatchOperation() {
            @Override
            public void run(BatchDatabase bDb) {
                Collection bCollection = bDb.getCollection(mCollection.getId());
                Map<String, TaskSpec> curTasks = sTasksByListMap.get(mCollection.getId());
                for (Map.Entry<String, TaskSpec> entry : curTasks.entrySet()) {
                    String rowKey = entry.getKey();
                    TaskSpec curSpec= entry.getValue();
                    TaskSpec newSpec = new TaskSpec(curSpec.getText(), curSpec.getAddedAt(), true);
                    bCollection.put(rowKey, newSpec);
                }
            }
        }, new Database.BatchOptions());
    }

    @Override
    public void addTask(TaskSpec task) {
        mCollection.put(UUID.randomUUID().toString(), task);
    }

    @Override
    public void updateTask(Task task) {
        mCollection.put(task.key, task.toSpec());
    }

    @Override
    public void deleteTask(String key) {
        mCollection.delete(key);
    }

    @Override
    public void setShowDone(boolean showDone) {
        mSettings.put(SHOW_DONE_KEY, showDone);
    }

    @Override
    public void close() {
        removeTodoListListener();
        super.close();
    }
}
