// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableList;

/**
 * A {@link DigitMapping} optimized for mappings where ranges of consecutive characters map to
 * consecutive values. The overall mapping need not be monotonic to take advantage of this
 * optimization.
 */
public class DigitRangeMapping implements DigitMapping {
    // TODO(rosswang): determine whether we should index these further
    private final ImmutableList<ContiguousSet<Character>> mRanges;
    private final int mRadix;

    /**
     * Constructs a {@code DigitRangeMapping} from a sequence of ranges (as {@link ContiguousSet}s).
     * Values are assigned to the characters in the order defined by flattening the ranges within
     * the interval. The behavior of this mapping is not defined if the ranges are not disjoint.
     *
     * @param ranges an iterable of disjoint contiguous sets of characters
     */
    public DigitRangeMapping(Iterable<ContiguousSet<Character>> ranges) {
        mRanges = ImmutableList.copyOf(ranges);
        int size = 0;
        for (ContiguousSet<Character> range : mRanges) {
            size += range.size();
        }
        mRadix = size;
    }

    @Override
    public char encode(int digit) {
        for (ContiguousSet<Character> range : mRanges) {
            if (digit < range.size()) {
                return range.asList().get(digit);
            }
            digit -= range.size();
        }
        throw new IllegalArgumentException("No encoding for digit " + digit +
                " (radix " + radix() + ")");
    }

    @Override
    public int decode(char encoded) {
        int digit = 0;
        for (ContiguousSet<Character> range : mRanges) {
            if (range.contains(encoded)) {
                return digit + encoded - range.first();
            }
            digit += range.size();
        }
        throw new IllegalArgumentException("Character " + encoded + " is not in digit mapping " +
            this);
    }

    @Override
    public int radix() {
        return mRadix;
    }

    @Override
    public String toString() {
        return mRanges.toString() + " (radix " + mRadix + ")";
    }
}
