// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Task is a Firebase-compatible class that tracks information regarding a particular task.
 *
 * @author alexfandrianto
 */
@JsonIgnoreProperties({ "key" })
public class Task implements KeyedData<Task> {
    private String text;
    private long addedAt;
    private boolean done;

    // Unserialized properties.
    private String key; // Usually assigned for comparison/viewing.

    // The default constructor is used by Firebase.
    public Task() {}

    // Use this constructor when creating a new Task for the first time.
    public Task(String text) {
        this.text = text;
        this.addedAt = System.currentTimeMillis();
        this.done = false;
    }

    public Task copy() {
        Task t = new Task();
        t.text = text;
        t.addedAt = addedAt;
        t.done = done;
        t.key = key;
        return t;
    }

    public String getText() {
        return text;
    }
    public long getAddedAt() {
        return addedAt;
    }
    public boolean getDone() {
        return done;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public String getKey() {
        return key;
    }

    public void setText(String newText) {
        text = newText;
    }
    public void setDone(boolean newDone) {
        done = newDone;
    }

    @Override
    public int compareTo(Task other) {
        if (done && !other.done) {
            return 1;
        } else if (!done && other.done) {
            return -1;
        }
        if (key == null && other.key != null) {
            return 1;
        } else if (key != null && other.key == null) {
            return -1;
        } else if (key == null && other.key == null) {
            return 0;
        }
        return key.compareTo(other.key);
    }
}
