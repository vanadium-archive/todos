// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;

/**
 * Basic Java adapter pattern for a {@link ChildEventListener}.
 */
public abstract class ChildEventListenerAdapter implements ChildEventListener {
    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
    }

    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String prevKey) {
    }

    @Override
    public void onCancelled(FirebaseError firebaseError) {
    }
}
