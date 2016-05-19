// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import android.app.Activity;
import android.os.Bundle;

import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;

public final class PersistenceFactory {
    private PersistenceFactory() {
    }

    /**
     * Indicates whether {@link #getMainPersistence(Activity, Bundle, ListEventListener)} may block.
     * This can affect whether a progress indicator is shown and whether a worker thread is used.
     */
    public static boolean mightGetMainPersistenceBlock() {
        return false;
    }

    /**
     * Instantiates a persistence object that can be used to manipulate todo lists.
     */
    public static MainPersistence getMainPersistence(Activity activity, Bundle savedInstanceState,
                                                     ListEventListener<ListMetadata> listener) {
        return new MockMainPersistence();
    }

    /**
     * Indicates whether {@link #getTodoListPersistence(Activity, Bundle, String, TodoListListener)}
     * may block. This can affect whether a progress indicator is shown and whether a worker thread
     * is used.
     */
    public static boolean mightGetTodoListPersistenceBlock() {
        return false;
    }

    /**
     * Instantiates a persistence object that can be used to manipulate a todo list.
     */
    public static TodoListPersistence getTodoListPersistence(
            Activity activity, Bundle savedInstanceState, String key, TodoListListener listener) {
        return new MockTodoListPersistence();
    }

    static class MockMainPersistence implements MainPersistence {
        @Override
        public void addTodoList(ListSpec listSpec) {
        }

        @Override
        public void deleteTodoList(String key) {
        }

        @Override
        public void close() {
        }

        @Override
        public String debugDetails() {
            return "";
        }
    }

    static class MockTodoListPersistence implements TodoListPersistence {
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

        @Override
        public void close() {
        }

        @Override
        public String debugDetails() {
            return "";
        }
    }
}
