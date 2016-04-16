// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import android.content.Context;

public final class PersistenceFactory {
    private PersistenceFactory(){}

    /**
     * Instantiates a persistence object that can be used to manipulate data.
     *
     * @param context an Android context, usually from an Android activity or application
     */
    public static Persistence getPersistence(Context context) {
        // TODO(rosswang): Choose this by build variant.
        return new FirebasePersistence(context);
    }
}
