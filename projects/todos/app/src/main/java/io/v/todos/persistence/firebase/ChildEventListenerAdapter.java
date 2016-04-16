// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;

import io.v.todos.KeyedData;
import io.v.todos.persistence.ListEventListener;

public class ChildEventListenerAdapter<T extends KeyedData> implements ChildEventListener {
    private final Class<T> mType;
    private final ListEventListener<T> mDelegate;

    public ChildEventListenerAdapter(Class<T> type, ListEventListener<T> delegate) {
        mType = type;
        mDelegate = delegate;
    }

    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
        T value = dataSnapshot.getValue(mType);
        value.setKey(dataSnapshot.getKey());
        mDelegate.onItemAdd(value);
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
        T value = dataSnapshot.getValue(mType);
        value.setKey(dataSnapshot.getKey());
        mDelegate.onItemUpdate(value);
    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        mDelegate.onItemDelete(dataSnapshot.getKey());
    }

    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String prevKey) {
    }

    @Override
    public void onCancelled(FirebaseError firebaseError) {
    }
}
