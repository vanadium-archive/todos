// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.util.Log;

import com.google.common.base.Function;
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

/**
 * This class aggregates Todo-list watch data from Syncbase into {@link ListMetadata}.
 */
public class MainListTracker {
    private static final String TAG = MainListTracker.class.getSimpleName();

    private final VContext mWatchContext;
    private final Collection mList;
    private final ListEventListener<ListMetadata> mListener;
    private ListSpec mListSpec;

    private final Map<String, Boolean> mIsTaskCompleted = new HashMap<>();
    private int mNumCompletedTasks;
    private boolean mListExistsLocally;

    public final ListenableFuture<Void> watchFuture;

    public MainListTracker(VContext vContext, Database database, String listId,
                           ListEventListener<ListMetadata> listener) {
        mList = database.getCollection(vContext, listId);
        mListener = listener;

        mWatchContext = vContext.withCancel();
        InputChannel<WatchChange> watch = database.watch(mWatchContext, mList.id(), "");
        watchFuture = InputChannels.withCallback(watch, new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange change) {
                processWatchChange(change);
                return null;
            }
        });
    }

    public ListenableFuture<Void> deleteList(VContext vContext) {
        // The watch context has to be cancelled first or else we may run into race conditions as
        // the collection is destroyed while the watch is still ongoing, which fails the watch with
        // a NoExistException. Alternatively we could just ignore that exception and not bother
        // cancelling the watch at all.
        mWatchContext.cancel();
        return Futures.transform(mList.destroy(vContext),
                new Function<Void, Void>() {
                    @Override
                    public Void apply(@Nullable Void input) {
                        mListener.onItemDelete(mList.id().getName());
                        return null;
                    }
                });
    }

    public ListMetadata getListMetadata() {
        return new ListMetadata(mList.id().getName(), mListSpec, mNumCompletedTasks,
                mIsTaskCompleted.size());
    }

    private void processWatchChange(WatchChange change) {
        String rowName = change.getRowName();

        if (rowName.equals(SyncbaseTodoList.LIST_METADATA_ROW_NAME)) {
            mListSpec = SyncbasePersistence.castWatchValue(change.getValue(), ListSpec.class);
        } else if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
            if (mIsTaskCompleted.remove(rowName)) {
                mNumCompletedTasks--;
            }
        } else {
            boolean isDone = SyncbasePersistence.castWatchValue(change.getValue(), TaskSpec.class)
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
