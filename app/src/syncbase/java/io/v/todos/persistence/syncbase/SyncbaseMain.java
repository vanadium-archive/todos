// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;
import io.v.v23.InputChannel;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.VFutures;
import io.v.v23.services.syncbase.BatchOptions;
import io.v.v23.services.syncbase.KeyValue;
import io.v.v23.syncbase.Batch;
import io.v.v23.syncbase.BatchDatabase;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.RowRange;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.vdl.VdlAny;
import io.v.v23.verror.ExistException;
import io.v.v23.verror.VException;
import io.v.v23.vom.VomUtil;

public class SyncbaseMain extends SyncbasePersistence implements MainPersistence {
    private static final String
            TAG = SyncbaseMain.class.getSimpleName(),
            MAIN_COLLECTION_NAME = "userdata",
            LISTS_PREFIX = "lists_";

    private static final Object sMainCollectionMutex = new Object();
    private static volatile Collection sMainCollection;

    public static boolean isInitialized() {
        return sMainCollection != null;
    }

    private final Map<String, MainListTracker> mTaskTrackers = new HashMap<>();

    /**
     * This constructor blocks until the instance is ready for use.
     */
    public SyncbaseMain(Activity activity, final ListEventListener<ListMetadata> listener)
            throws VException, SyncbaseServer.StartException {
        super(activity);

        synchronized (sMainCollectionMutex) {
            if (sMainCollection == null) {
                Collection mainCollection = getDatabase()
                        .getCollection(mVContext, MAIN_COLLECTION_NAME);
                try {
                    VFutures.sync(mainCollection.create(mVContext, null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sMainCollection = mainCollection;
            }
        }

        InputChannel<WatchChange> watch = getDatabase().watch(
                mVContext, sMainCollection.id(), LISTS_PREFIX);
        trap(InputChannels.withCallback(watch, new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange change) {
                String listId = change.getRowName();

                if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
                    trap(mTaskTrackers.remove(listId).deleteList(mVContext));
                } else {
                    MainListTracker listTracker = new MainListTracker(
                            mVContext, getDatabase(), listId, listener);
                    if (mTaskTrackers.put(listId, listTracker) != null) {
                        // List entries in the main collection are just ( list ID => nil ), so we
                        // never expect updates other than an initial add...
                        Log.w(TAG, "Unexpected update to " + MAIN_COLLECTION_NAME + " collection " +
                                "for list " + listId);
                    }

                    trap(listTracker.watchFuture);
                }
                return null;
            }
        }));
    }

    @Override
    public void addTodoList(final ListSpec listSpec) {
        final String listName = LISTS_PREFIX + UUID.randomUUID().toString().replace('-', '_');
        final Collection listCollection = getDatabase().getCollection(mVContext, listName);
        Futures.addCallback(listCollection.create(mVContext, null),
                new TrappingCallback<Void>(mActivity) {
                    @Override
                    public void onSuccess(@Nullable Void result) {
                        // These can happen in either order
                        trap(sMainCollection.put(mVContext, listName, null, VdlAny.class));
                        trap(listCollection.put(mVContext, SyncbaseTodoList.LIST_ROW_NAME, listSpec,
                                ListSpec.class));
                    }
                });
    }

    @Override
    public void deleteTodoList(String key) {
        trap(sMainCollection.delete(mVContext, key));
    }

    @Override
    public void completeAllTasks(ListMetadata listMetadata) {
        final String listId = listMetadata.key;
        trap(Batch.runInBatch(mVContext, getDatabase(), new BatchOptions(),
                new Batch.BatchOperation() {
                    @Override
                    public ListenableFuture<Void> run(BatchDatabase db) {
                        final Collection list = db.getCollection(mVContext, listId);

                        InputChannel<KeyValue> scan = list.scan(mVContext,
                                RowRange.prefix(SyncbaseTodoList.TASKS_PREFIX));
                        InputChannel<ListenableFuture<Void>> puts = InputChannels.transform(
                                mVContext, scan, new InputChannels.TransformFunction<KeyValue,
                                        ListenableFuture<Void>>() {
                                    @Override
                                    public ListenableFuture<Void> apply(KeyValue kv)
                                            throws VException {
                                        TaskSpec taskSpec =
                                                (TaskSpec) VomUtil.decode(kv.getValue());
                                        taskSpec.setDone(true);
                                        return list.put(mVContext, kv.getKey(), taskSpec,
                                                TaskSpec.class);
                                    }
                                });

                        return Futures.transform(Futures.allAsList(InputChannels.asIterable(puts)),
                                new Function<List<Void>, Void>() {
                                    @Nullable
                                    @Override
                                    public Void apply(@Nullable List<Void> input) {
                                        return null;
                                    }
                                });
                    }
                }));
    }
}
