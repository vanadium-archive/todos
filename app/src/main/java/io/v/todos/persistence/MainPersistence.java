// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;

public interface MainPersistence extends Persistence {
    void addTodoList(ListSpec listSpec);
    void deleteTodoList(String key);
    void completeAllTasks(ListMetadata listMetadata);
}
