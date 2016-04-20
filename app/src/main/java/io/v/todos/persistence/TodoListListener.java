// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import io.v.todos.model.Task;
import io.v.todos.model.ListMetadata;

public interface TodoListListener extends ListEventListener<Task> {
    void onUpdate(ListMetadata value);
    void onDelete();
}
