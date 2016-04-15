// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import java.util.ArrayList;
import java.util.Collections;

/**
 * DataList is an ArrayList with additional helper methods to keep the entries sorted.
 * This list requires each entry to have a unique key.
 *
 * TODO(alexfandrianto): This should have tests.
 *
 * @author alexfandrianto
 */
public class DataList<T extends KeyedData<T>> extends ArrayList<T> {
    public void insertInOrder(T item) {
        // Note: binarySearch returns -|correct insert index| - 1 if it fails to find a match.
        // For Java ints, this is the bitwise complement of the "correct" insertion index.
        int insertIndex = Collections.binarySearch(this, item);
        add(insertIndex < 0 ? ~insertIndex : insertIndex, item);
    }

    // We have to replace the old item while keeping sort order.
    // It is easiest to remove and then insertInOrder.
    public void updateInOrder(T item) {
        removeByKey(item.getKey());
        insertInOrder(item);
    }

    public void removeByKey(String key) {
        int index = findIndexByKey(key);
        if (index != -1) {
            remove(index);
        }
    }

    private int findIndexByKey(String key) {
        for (int i = 0; i < size(); i++) {
            T oldItem = get(i);
            if (oldItem.getKey().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public T findByKey(String key) {
        int index = findIndexByKey(key);
        return index == -1 ? null : get(index);
    }


}
