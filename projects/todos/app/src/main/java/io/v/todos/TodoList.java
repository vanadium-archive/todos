// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TodoList is a Firebase-compatible class that tracks information regarding a particular todo list.
 *
 * Created by alexfandrianto on 4/11/16.
 */
@JsonIgnoreProperties({ "numCompleted", "numTasks", "done", "key" })
public class TodoList extends KeyedData<TodoList> {
    private String name;
    private long updatedAt;

    // Not serialized.
    public int numCompleted = 0;
    public int numTasks = 0;
    private String key = null; // Usually assigned for comparison/viewing.
    //public List<String> sharedWith ??

    // The default constructor is used by Firebase.
    public TodoList() {}

    // Use this constructor when creating a new Task for the first time.
    public TodoList(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }
    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean getDone() {
        return numTasks > 0 && numCompleted == numTasks;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public String getKey() {
        return key;
    }

    @Override
    public int compareTo(TodoList other) {
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
