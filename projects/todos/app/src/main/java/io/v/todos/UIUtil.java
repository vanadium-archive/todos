// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.text.format.DateUtils;

/**
 * Created by alexfandrianto on 4/18/16.
 */
public class UIUtil {
    public static String computeTimeAgo(String prefix, long startTime) {
        return prefix + ": " + DateUtils.getRelativeTimeSpanString(startTime);
    }
}
