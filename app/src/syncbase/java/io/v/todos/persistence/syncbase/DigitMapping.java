// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

public interface DigitMapping {
    char encode(int digit);
    int decode(char encoded);
    int radix();
}
