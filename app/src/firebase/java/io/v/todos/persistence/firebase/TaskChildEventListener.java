// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import com.firebase.client.DataSnapshot;

import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.ListEventListener;

public class TaskChildEventListener extends ChildEventListenerAdapter {
    private final ListEventListener<Task> mDelegate;

    public TaskChildEventListener(ListEventListener<Task> delegate) {
        mDelegate = delegate;
    }

    protected Task extractValue(DataSnapshot dataSnapshot) {
        return new Task(dataSnapshot.getKey(), dataSnapshot.getValue(TaskSpec.class));
    }

    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
        mDelegate.onItemAdd(extractValue(dataSnapshot));
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
        mDelegate.onItemUpdate(extractValue(dataSnapshot));
    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        mDelegate.onItemDelete(dataSnapshot.getKey());
    }
}
