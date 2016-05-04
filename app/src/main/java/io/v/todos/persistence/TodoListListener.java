// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import java.util.List;

import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;

public interface TodoListListener extends ListEventListener<Task> {
    void onUpdate(ListSpec value);
    void onDelete();
    void onUpdateShowDone(boolean showDone);

    void onShareChanged(List<String> sharedTo);
}
