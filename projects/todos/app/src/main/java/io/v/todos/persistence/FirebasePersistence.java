// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import android.content.Context;

import com.firebase.client.Firebase;

/**
 * TODO(alexfandrianto): We may want to shove a lot more into this class and have it subclass
 * a common interface with Syncbase. I want this to also manage data watches and changes.
 *
 * @author alexfandrianto
 */
public class FirebasePersistence implements Persistence {
    static {
        // Set up Firebase to persist data locally even when offline. This must be set before
        // Firebase is used.
        Firebase.getDefaultConfig().setPersistenceEnabled(true);
    }

    /**
     * Instantiates a persistence object that can be used to manipulate data.
     *
     * @param context an Android context, usually from an Android activity or application
     */
    public FirebasePersistence(Context context) {
        // This no-ops if the context has already been set, and calls getApplicationContext so we
        // don't have to worry about leaking activity contexts.
        Firebase.setAndroidContext(context);
    }

    @Override
    public void close() {
        // TODO(rosswang): Remove listeners.
    }
}
