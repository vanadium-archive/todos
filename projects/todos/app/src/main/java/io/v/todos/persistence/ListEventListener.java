// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

public interface ListEventListener<T> {
    void onInsert(T item);
    void onUpdate(T item);
    void onDelete(String key);
}
