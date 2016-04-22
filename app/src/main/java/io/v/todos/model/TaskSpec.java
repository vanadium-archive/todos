// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.model;

import java.io.Serializable;

/**
 * POJO of persisted information regarding a particular task.
 *
 * @author alexfandrianto
 */
public class TaskSpec implements Serializable {
    private String mText;
    private long mAddedAt;
    private boolean mDone;

    public TaskSpec() {}

    public TaskSpec(String text, long addedAt, boolean done) {
        mText = text;
        mAddedAt = addedAt;
        mDone = done;
    }

    /**
     * Convenience constructor that creates an undone task with an {@code addedAt} timestamp of now.
     */
    public TaskSpec(String text) {
        this(text, System.currentTimeMillis(), false);
    }

    public String getText() {
        return mText;
    }

    public void setText(String value) {
        mText = value;
    }

    public long getAddedAt() {
        return mAddedAt;
    }

    public void setAddedAt(long value) {
        mAddedAt = value;
    }

    public boolean getDone() {
        return mDone;
    }

    public void setDone(boolean value) {
        mDone = value;
    }
}
