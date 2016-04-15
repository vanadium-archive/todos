// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

/**
 * KeyedData represents data that has a key and is comparable.
 * Most subclasses will use this key as part of their comparison function.
 *
 * @author alexfandrianto
 */
public abstract class KeyedData<T> implements Comparable<T> {
    public abstract String getKey();
}
