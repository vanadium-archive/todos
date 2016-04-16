// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import android.content.Context;

import com.firebase.client.Firebase;

import io.v.todos.persistence.Persistence;

/**
 * TODO(alexfandrianto): We may want to shove a lot more into this class and have it subclass
 * a common interface with Syncbase. I want this to also manage data watches and changes.
 *
 * @author alexfandrianto
 */
public abstract class FirebasePersistence implements Persistence {
    private static final String FIREBASE_EXAMPLE_URL = "https://vivid-heat-7354.firebaseio.com/";

    static {
        // Set up Firebase to persist data locally even when offline. This must be set before
        // Firebase is used.
        Firebase.getDefaultConfig().setPersistenceEnabled(true);
    }

    private final Firebase mFirebase;

    protected Firebase getFirebase() {
        return mFirebase;
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

        mFirebase = new Firebase(FIREBASE_EXAMPLE_URL);
    }
}
