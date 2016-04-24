// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import com.google.common.collect.Range;

public final class IdAlphabets {
    private IdAlphabets() {
    }

    public static final IdAlphabet
            COLLECTION_ID = fromDigitMappings(
            DigitMappings.fromRangeSet(
                    Range.closed('A', 'Z'),
                    Range.closed('a', 'z')), DigitMappings.fromRangeSet(
                    Range.closed('0', '9'),
                    Range.closed('A', 'Z'),
                    Range.singleton('_'),
                    Range.closed('a', 'z'))
    ),
            ROW_NAME = fromDigitMapping(DigitMappings.ALL);

    /**
     * Constructs an {@code IdAlphabet} with the given {@link DigitMapping} used for all digits.
     */
    public static IdAlphabet fromDigitMapping(final DigitMapping digitMapping) {
        return fromDigitMappings(digitMapping, digitMapping);
    }

    /**
     * Constructs an {@code IdAlphabet} with possibly distinct {@link DigitMapping}s for the first
     * digit and the remaining digits.
     *
     * @param leadingDigitMapping the mapping to use for the leading digit
     * @param digitMapping the mapping to use for all digits except the leading digit
     */
    public static IdAlphabet fromDigitMappings(final DigitMapping leadingDigitMapping,
                                               final DigitMapping digitMapping) {
        return new IdAlphabet() {
            @Override
            public char encodeDigit(int digit) {
                return digitMapping.encode(digit);
            }

            @Override
            public int decodeDigit(char encoded) {
                return digitMapping.decode(encoded);
            }

            @Override
            public char encodeLeadingDigit(int digit) {
                return leadingDigitMapping.encode(digit);
            }

            @Override
            public int decodeLeadingDigit(char encoded) {
                return leadingDigitMapping.decode(encoded);
            }

            @Override
            public int radix() {
                return digitMapping.radix();
            }

            @Override
            public int leadingRadix() {
                return leadingDigitMapping.radix();
            }
        };
    }
}
