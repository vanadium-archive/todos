// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.support.annotation.NonNull;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;

public final class DigitMappings {
    private DigitMappings() {
    }

    public static final DiscreteDomain<Character> DOMAIN = new DiscreteDomain<Character>() {
        @Override
        public Character next(@NonNull Character value) {
            return value == Character.MAX_VALUE ? null : (char) (value + 1);
        }

        @Override
        public Character previous(@NonNull Character value) {
            return value == Character.MIN_VALUE ? null : (char) (value - 1);
        }

        @Override
        public long distance(@NonNull Character start, @NonNull Character end) {
            return end - start;
        }

        @Override
        public Character maxValue() {
            return Character.MAX_VALUE;
        }

        @Override
        public Character minValue() {
            return Character.MIN_VALUE;
        }
    };

    public static final DigitMapping ALL = fromRangeSet(Range.<Character>all());

    /**
     * Constructs a {@link DigitMapping} from the given ranges, where the ranges are given in order
     * of increasing significance. Although the ranges may jump around in significance, within each
     * range, characters are still mapped to digits in ascending order. Out-of-order ranges may be
     * used as well but cannot be optimized in this way.
     *
     * @see DigitRangeMapping
     */
    public static DigitMapping fromRanges(Iterable<ContiguousSet<Character>> ranges) {
        return new DigitRangeMapping(ranges);
    }

    /**
     * @see #fromRangeSet(RangeSet)
     */
    @SafeVarargs
    public static DigitMapping fromRangeSet(Range<Character>... ranges) {
        ImmutableRangeSet.Builder<Character> builder = ImmutableRangeSet.builder();
        for (Range<Character> range : ranges) {
            builder.add(range);
        }
        return fromRangeSet(builder.build());
    }

    /**
     * Constructs a {@link DigitMapping} from a {@link RangeSet}, ordering the ranges in ascending
     * order (regardless of input order). If out-of-order ranges are desired, use the
     * {@link #fromRanges(Iterable)} factory method instead to provide explicit ordering.
     */
    public static DigitMapping fromRangeSet(RangeSet<Character> spec) {
        return fromRanges(Collections2.transform(spec.asRanges(),
                new Function<Range<Character>, ContiguousSet<Character>>() {
                    @Override
                    public ContiguousSet<Character> apply(Range<Character> input) {
                        return ContiguousSet.create(input, DOMAIN);
                    }
                }));
    }
}
