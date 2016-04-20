// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import io.v.todos.Task;
import io.v.todos.TodoList;

public interface TodoListPersistence extends Persistence {
    void updateTodoList(TodoList todoList);
    void deleteTodoList();
    void addTask(Task task);
    void updateTask(Task task);
    void deleteTask(String key);
}
