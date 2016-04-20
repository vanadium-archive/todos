// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.model;

/**
 * POJO of persisted information regarding a particular todo list.
 */
public class ListSpec {
    private String mName;
    private long mUpdatedAt;

    public ListSpec() {}

    public ListSpec(String name, long updatedAt) {
        mName = name;
        mUpdatedAt = updatedAt;
    }

    /**
     * Convenience constructor that initializes {@code updatedAt} to now.
     */
    public ListSpec(String name) {
        this(name, System.currentTimeMillis());
    }

    public String getName() {
        return mName;
    }

    public void setName(String value) {
        mName = value;
    }

    public long getUpdatedAt() {
        return mUpdatedAt;
    }

    public void setUpdatedAt(long value) {
        mUpdatedAt = value;
    }
}
