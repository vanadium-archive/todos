// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;

import io.v.todos.KeyedData;

public class FirebaseChildEventListenerAdapter<T extends KeyedData> implements ChildEventListener {
    private final Class<T> mType;
    private final ListEventListener<T> mDelegate;

    public FirebaseChildEventListenerAdapter(Class<T> type, ListEventListener<T> delegate) {
        mType = type;
        mDelegate = delegate;
    }

    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
        T value = dataSnapshot.getValue(mType);
        value.setKey(dataSnapshot.getKey());
        mDelegate.onInsert(value);
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
        T value = dataSnapshot.getValue(mType);
        value.setKey(dataSnapshot.getKey());
        mDelegate.onUpdate(value);
    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        mDelegate.onDelete(dataSnapshot.getKey());
    }

    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String prevKey) {
    }

    @Override
    public void onCancelled(FirebaseError firebaseError) {
    }
}
