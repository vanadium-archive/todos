// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

public interface Persistence extends AutoCloseable {
    void close();

    /**
     * debugDetails provides information useful for debugging the state of the Persistence object.
     *
     * @return textual content to be shared with a human being to describe or inspect the state
     * of the object.
     */
    String debugDetails();
}
