// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;

public interface TodoListPersistence extends Persistence {
    void updateTodoList(ListSpec listSpec);
    void deleteTodoList();
    void completeTodoList();
    void addTask(TaskSpec task);
    void updateTask(Task task);
    void deleteTask(String key);
    void setShowDone(boolean showDone);
}
