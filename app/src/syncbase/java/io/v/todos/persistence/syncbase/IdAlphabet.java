// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

/**
 * Represents a mapping from a sequence of digits to a string. For now, the only differentiation we
 * support is between the leading digit and all other digits. Eventually, we will want to expose
 * {@code getDigitMapping(int fromLeft, int fromRight)} or similar instead, with additional methods
 * to determine encoding length and other alphabet-dependent needs.
 */
public interface IdAlphabet {
    char encodeDigit(int digit);
    int decodeDigit(char encoded);

    char encodeLeadingDigit(int digit);
    int decodeLeadingDigit(char encoded);

    int radix();
    int leadingRadix();
}
