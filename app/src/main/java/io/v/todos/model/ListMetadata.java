// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.model;

import android.support.annotation.NonNull;

/**
 * Tracks information regarding a particular todo list.
 */
public class ListMetadata extends KeyedData<ListMetadata> {
    public final String name;
    public final long updatedAt;

    public final int numCompleted;
    public final int numTasks;

    public ListMetadata(String key, String name, long updatedAt, int numCompleted, int numTasks) {
        super(key);
        this.name = name;
        this.updatedAt = updatedAt;
        this.numCompleted = numCompleted;
        this.numTasks = numTasks;
    }

    public ListMetadata(String key, ListSpec spec, int numCompleted, int numTasks) {
        this(key, spec.getName(), spec.getUpdatedAt(), numCompleted, numTasks);
    }

    public boolean isDone() {
        return numTasks > 0 && numCompleted == numTasks;
    }

    public boolean canCompleteAll() {
        return numCompleted < numTasks;
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                o instanceof ListMetadata &&
                ((ListMetadata) o).canEqual(this) &&
                compareTo((ListMetadata)o) == 0;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ListMetadata;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public int compareTo(@NonNull ListMetadata other) {
        if (this == other) {
            return 0;
        } else if (!other.canEqual(this)) {
            throw new ClassCastException("Cannot compare " + getClass() + " to " +
                    other.getClass());
        } else {
            return key.compareTo(other.key);
        }
        // TODO(rosswang): factor out ordering.
    }

    public ListSpec toSpec() {
        return new ListSpec(name, updatedAt);
    }
}
