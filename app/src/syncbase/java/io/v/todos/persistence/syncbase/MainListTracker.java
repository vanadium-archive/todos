// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.v23.InputChannel;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.context.VContext;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.Database;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.verror.NoExistException;

/**
 * This class aggregates Todo-list watch data from Syncbase into {@link ListMetadata}.
 */
public class MainListTracker {
    private static final String TAG = MainListTracker.class.getSimpleName();

    private final ListEventListener<ListMetadata> mListener;
    private ListSpec mListSpec;

    private final Map<String, Boolean> mIsTaskCompleted = new HashMap<>();
    private int mNumCompletedTasks;
    private boolean mListExistsLocally;

    public final Collection collection;
    public final ListenableFuture<Void> watchFuture;

    public MainListTracker(VContext vContext, Database database, final String listId,
                           ListEventListener<ListMetadata> listener) {
        collection = database.getCollection(vContext, listId);
        mListener = listener;
        InputChannel<WatchChange> watch = database.watch(vContext, collection.id(), "");
        watchFuture = InputChannels.withCallback(watch, new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange change) {
                processWatchChange(change);
                return null;
            }
        });

        // If the watch fails with NoExistException, the collection has been deleted.
        Futures.addCallback(watchFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                if (t instanceof NoExistException && mListExistsLocally) {
                    Log.d(TAG, listId + " destroyed");
                    mListener.onItemDelete(listId);
                }
            }
        });
    }

    public ListMetadata getListMetadata() {
        return new ListMetadata(collection.id().getName(), mListSpec, mNumCompletedTasks,
                mIsTaskCompleted.size());
    }

    private void processWatchChange(WatchChange change) {
        String rowName = change.getRowName();

        if (rowName.equals(SyncbaseTodoList.LIST_METADATA_ROW_NAME)) {
            mListSpec = SyncbasePersistence.castFromSyncbase(change.getValue(), ListSpec.class);
        } else if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
            if (mIsTaskCompleted.remove(rowName)) {
                mNumCompletedTasks--;
            }
        } else {
            boolean isDone = SyncbasePersistence.castFromSyncbase(change.getValue(), TaskSpec.class)
                    .getDone();
            Boolean rawWasDone = mIsTaskCompleted.put(rowName, isDone);
            boolean wasDone = rawWasDone != null && rawWasDone;
            if (!wasDone && isDone) {
                mNumCompletedTasks++;
            } else if (wasDone && !isDone) {
                mNumCompletedTasks--;
            }
        }

        // Don't fire events until we've processed the entire batch of watch events.
        if (!change.isContinued()) {
            ListMetadata listMetadata = getListMetadata();
            Log.d(TAG, listMetadata.toString());

            if (mListExistsLocally) {
                mListener.onItemUpdate(listMetadata);
            } else {
                mListExistsLocally = true;
                mListener.onItemAdd(listMetadata);
            }
        }
    }
}
