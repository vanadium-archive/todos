// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IdGeneratorTest {
    private static final Pattern COLLECTION_ID_PATTERN = Pattern.compile("[0-9A-Za-z][0-9A-Za-z_]*");
    private static final Random RANDOM = new Random();

    @Test
    public void testLongToPaddedIdentifier() {
        IdGenerator
                testGeneratorA = new IdGenerator(IdAlphabets.COLLECTION_ID, true),
                testGeneratorB = new IdGenerator(IdAlphabets.ROW_NAME, true);

        SortedSet<Long> sortedInputs = new TreeSet<>(Arrays.asList(
                Long.MIN_VALUE,
                Long.MIN_VALUE + 1,
                Long.MIN_VALUE + 2,
                -1L,
                0L,
                1L,
                Long.MAX_VALUE - 2,
                Long.MAX_VALUE - 1,
                Long.MAX_VALUE));
        for (int i = 0; i < 50; i++) {
            sortedInputs.add(RANDOM.nextLong());
        }

        String lastIdA = null, lastIdB = null;
        for (long input : sortedInputs) {
            String testId = testGeneratorA.longToPaddedIdentifier(input);
            if (lastIdA != null) {
                assertTrue("IdGenerator.longToPaddedIdentifier generates IDs consistent with " +
                                "input ordering (" + lastIdA + " < " + testId + ")",
                        testId.compareTo(lastIdA) > 0);
            }
            lastIdA = testId;

            testId = testGeneratorB.longToPaddedIdentifier(input);
            if (lastIdB != null) {
                assertTrue("IdGenerator.longToPaddedIdentifier generates IDs consistent with " +
                                "input ordering (" + lastIdB + " < " + testId + ")",
                        testId.compareTo(lastIdB) > 0);
            }
            lastIdB = testId;
        }
    }

    @Test
    public void testDecodeChar() {
        for (int i = 0; i < IdAlphabets.COLLECTION_ID.radix(); i++) {
            char testChar = IdAlphabets.COLLECTION_ID.encodeDigit(i);
            assertEquals("IdGenerator.decodeChar('" + testChar + "')",
                    i, IdAlphabets.COLLECTION_ID.decodeDigit(testChar));
        }
    }

    private void verifyIncrement(String input, String incremented) {
        String diagnostic = input + " + 1 = " + incremented;
        assertTrue("IdGenerator.increment generates larger IDs than the input (" + diagnostic + ")",
                incremented.compareTo(input) > 0);
        assertTrue("IdGenerator.increment generates legal IDs (" + diagnostic + ")",
                COLLECTION_ID_PATTERN.matcher(incremented).matches());
    }

    @Test
    public void testIncrement() {
        IdGenerator testGenerator = new IdGenerator(IdAlphabets.COLLECTION_ID, true);

        String[] inputs = new String[]{
                "0000",
                "A",
                "AZ00",
                "Az00",
                "Zz00",
                "zz00",
                "zzz0",
                "zzzz"
        };

        for (String input : inputs) {
            verifyIncrement(input, testGenerator.increment(input, 2));
        }

        for (int i = 0; i < 50; i++) {
            String input = testGenerator.longToPaddedIdentifier(RANDOM.nextLong());
            verifyIncrement(input, testGenerator.increment(input, RANDOM.nextInt(input.length())));
        }
    }
}