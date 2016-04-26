// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import org.junit.Test;

import io.v.todos.model.DataList;
import io.v.todos.model.KeyedData;

import static org.junit.Assert.assertEquals;

/**
 * A unit test for the DataList. Confirms that keyed data is inserted in the correct order.
 */
public class DataListTest {
    @Test
    public void dataListTest() throws Exception {
        DataList<TestKeyedData> dataList = new DataList<>();

        // Insert a few.
        assertEquals(0, dataList.insertInOrder(new TestKeyedData("b")));
        assertEquals(1, dataList.insertInOrder(new TestKeyedData("d")));
        assertEquals(0, dataList.insertInOrder(new TestKeyedData("a")));
        assertEquals(2, dataList.insertInOrder(new TestKeyedData("c")));
        assertEquals(0, dataList.insertInOrder(new TestKeyedData("e", -1)));
        assertEquals(5, dataList.insertInOrder(new TestKeyedData("g", 1)));
        assertEquals(5, dataList.insertInOrder(new TestKeyedData("f", 1)));

        // We should be at e, a, b, c, d, f, g now.
        assertEquals(1, dataList.findIndexByKey("a"));
        assertEquals(2, dataList.findIndexByKey("b"));
        assertEquals(3, dataList.findIndexByKey("c"));
        assertEquals(4, dataList.findIndexByKey("d"));
        assertEquals(0, dataList.findIndexByKey("e"));
        assertEquals(5, dataList.findIndexByKey("f"));
        assertEquals(6, dataList.findIndexByKey("g"));
        assertEquals(-1, dataList.findIndexByKey("h"));

        // Update a few.
        assertEquals(6, dataList.updateInOrder(new TestKeyedData("b", 2))); // Move b to the back.
        assertEquals(5, dataList.updateInOrder(new TestKeyedData("a", 2))); // Move a too.

        // Confirm priorities. We should be at e, c, d, f, g, a, b now.
        assertEquals(2, dataList.findByKey("a").priority);
        assertEquals(2, dataList.findByKey("b").priority);
        assertEquals(0, dataList.findByKey("c").priority);
        assertEquals(0, dataList.findByKey("d").priority);
        assertEquals(-1, dataList.findByKey("e").priority);
        assertEquals(1, dataList.findByKey("f").priority);
        assertEquals(1, dataList.findByKey("g").priority);

        // And now, remove a few.
        assertEquals(1, dataList.removeByKey("c"));
        assertEquals(4, dataList.removeByKey("a"));

        // Confirm indexes. e, d, f, g, b
        assertEquals(-1, dataList.findIndexByKey("a"));
        assertEquals(4, dataList.findIndexByKey("b"));
        assertEquals(-1, dataList.findIndexByKey("c"));
        assertEquals(1, dataList.findIndexByKey("d"));
        assertEquals(0, dataList.findIndexByKey("e"));
        assertEquals(2, dataList.findIndexByKey("f"));
        assertEquals(3, dataList.findIndexByKey("g"));
        assertEquals(-1, dataList.findIndexByKey("h"));
    }

    private class TestKeyedData extends KeyedData<TestKeyedData> {
        int priority;

        TestKeyedData(String key) {
            super(key);
        }

        TestKeyedData(String key, int priority) {
            super(key);
            this.priority = priority;
        }

        @Override
        public int compareTo(TestKeyedData testKeyedData) {
            if (priority != testKeyedData.priority) {
                return priority - testKeyedData.priority;
            }
            return key.compareTo(testKeyedData.key);
        }
    }
}