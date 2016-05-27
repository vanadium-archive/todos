// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;

import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;

public class SyncbaseMain extends SyncbasePersistence implements MainPersistence {
    public SyncbaseMain(Activity activity, Bundle savedInstanceState,
                        ListEventListener<ListMetadata> listener) {
        super(activity, savedInstanceState);
    }

    @Override
    public String addTodoList(ListSpec listSpec) {
        return null;
    }

    @Override
    public void deleteTodoList(String key) {

    }
}
