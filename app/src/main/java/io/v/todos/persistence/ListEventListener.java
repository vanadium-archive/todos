// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

public interface ListEventListener<T> {
    void onItemAdd(T item);
    void onItemUpdate(T item);
    void onItemDelete(String key);
}
