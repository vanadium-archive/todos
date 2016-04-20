// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.model;

import android.support.annotation.NonNull;

import java.util.Objects;

/**
 * Task is a Firebase-compatible class that tracks information regarding a particular task.
 *
 * @author alexfandrianto
 */
public class Task extends KeyedData<Task> {
    public final String text;
    public final long addedAt;
    public final boolean done;

    // Use this constructor when creating a new Task for the first time.
    public Task(String key, String text, long addedAt, boolean done) {
        super(key);
        this.text = text;
        this.addedAt = addedAt;
        this.done = done;
    }

    public Task(String key, TaskSpec spec) {
        this(key, spec.getText(), spec.getAddedAt(), spec.getDone());
    }

    public Task withText(String value) {
        return Objects.equals(text, value) ? this : new Task(key, value, addedAt, done);
    }

    public Task withToggleDone() {
        return new Task(key, text, addedAt, !done);
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                o instanceof Task &&
                ((Task) o).canEqual(this) &&
                compareTo((Task)o) == 0;
    }

    protected boolean canEqual(Object other) {
        return other instanceof Task;
    }

    @Override
    public int hashCode() {
        return Objects.hash(addedAt, done);
    }

    @Override
    public int compareTo(@NonNull Task other) {
        // TODO(rosswang): factor out ordering.
        if (other == this) {
            return 0;
        } else if (!other.canEqual(this)) {
            throw new ClassCastException("Cannot compare " + getClass() + " to " +
                    other.getClass());
        } else if (done && !other.done) {
            return 1;
        } else if (!done && other.done) {
            return -1;
        } else if (key == null && other.key != null) {
            return 1;
        } else if (key != null && other.key == null) {
            return -1;
        } else if (key == null) {
            return 0;
        } else {
            return key.compareTo(other.key);
        }
    }

    public TaskSpec toSpec() {
        return new TaskSpec(text, addedAt, done);
    }
}
