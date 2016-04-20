// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import io.v.todos.model.ListMetadata;

public interface MainPersistence extends Persistence {
    void addTodoList(ListMetadata listMetadata);
    void deleteTodoList(String key);
    void completeAllTasks(ListMetadata listMetadata);
}
