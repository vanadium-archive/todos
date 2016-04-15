// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.Context;

import com.firebase.client.Firebase;

/**
 * TODO(alexfandrianto): We may want to shove a lot more into this singleton and have it subclass
 * a common interface with Syncbase. I want this to also manage data watches and changes.
 *
 * @author alexfandrianto
 */
public class FirebaseSingleton {
    private FirebaseSingleton() {} // Marked private to prevent accidental instantiation.

    private static FirebaseSingleton singleton;

    /**
     * Obtain a database singleton that can be used to manipulate data.
     *
     * @param context An Android context, usually from an Android activity or application.
     * @return
     */
    public synchronized static FirebaseSingleton getDatabase(Context context) {
        if (singleton == null) {
            singleton = new FirebaseSingleton();

            // Set up Firebase with the context and have it persist data locally even when offline.
            Firebase.setAndroidContext(context);
            Firebase.getDefaultConfig().setPersistenceEnabled(true);
        }
        return singleton;
    }
}
