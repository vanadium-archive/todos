// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

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
import io.v.v23.security.access.Permissions;
import io.v.v23.services.syncbase.BatchOptions;
import io.v.v23.services.syncbase.CollectionRow;
import io.v.v23.services.syncbase.Id;
import io.v.v23.services.syncbase.KeyValue;
import io.v.v23.services.syncbase.SyncgroupJoinFailedException;
import io.v.v23.services.syncbase.SyncgroupMemberInfo;
import io.v.v23.services.syncbase.SyncgroupSpec;
import io.v.v23.syncbase.Batch;
import io.v.v23.syncbase.BatchDatabase;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.RowRange;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.vdl.VdlAny;
import io.v.v23.verror.NoExistException;
import io.v.v23.verror.VException;

public class SyncbaseMain extends SyncbasePersistence implements MainPersistence {
    private static final String
            TAG = SyncbaseMain.class.getSimpleName(),
            LISTS_PREFIX = "lists_";

    private final IdGenerator mIdGenerator = new IdGenerator(IdAlphabets.COLLECTION_ID, true);
    private final Map<String, MainListTracker> mTaskTrackers = new HashMap<>();

    /**
     * This constructor blocks until the instance is ready for use.
     */
    public SyncbaseMain(Activity activity, Bundle savedInstanceState,
                        final ListEventListener<ListMetadata> listener)
            throws VException, SyncbaseServer.StartException {
        super(activity, savedInstanceState);

        // Prepare a watch on top of the userdata collection to determine which todo lists need to
        // be tracked by this application.
        InputChannel<WatchChange> watch = getDatabase().watch(getVContext(),
                getUserCollection().id(), LISTS_PREFIX);
        trap(InputChannels.withCallback(watch, new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange change) {
                final String listId = change.getRowName();

                if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
                    // (this is idempotent)
                    Log.d(TAG, listId + " removed from index");
                    deleteTodoList(listId);
                } else {
                    mIdGenerator.registerId(change.getRowName().substring(LISTS_PREFIX.length()));

                    Log.d(TAG, "Found a list id from userdata watch: " + listId);
                    Futures.catchingAsync(joinListSyncgroup(listId),
                            SyncgroupJoinFailedException.class, new
                                    AsyncFunction<SyncgroupJoinFailedException, SyncgroupSpec>() {
                                        public ListenableFuture<SyncgroupSpec> apply(@Nullable
                                                                                     SyncgroupJoinFailedException
                                                                                             input) throws
                                                Exception {
                                            Log.d(TAG, "Join failed. Sleeping and trying again: "
                                                    + listId);
                                            return sExecutor.schedule(new Callable<SyncgroupSpec>
                                                    () {

                                                @Override
                                                public SyncgroupSpec call() throws Exception {
                                                    Log.d(TAG, "Sleep done. Trying again: " +
                                                            listId);

                                                    // If this errors, then we will not get
                                                    // another chance to see
                                                    // this syncgroup until the app is restarted.
                                                    return joinListSyncgroup(listId).get();
                                                }
                                            }, RETRY_DELAY, TimeUnit.MILLISECONDS);
                                        }
                                    });

                    MainListTracker listTracker = new MainListTracker(getVContext(), getDatabase(),
                            listId, listener);
                    if (mTaskTrackers.put(listId, listTracker) != null) {
                        // List entries in the main collection are just ( list ID => nil ), so we
                        // never expect updates other than an initial add...
                        Log.w(TAG, "Unexpected update to " + USER_COLLECTION_NAME + " collection " +
                                "for list " + listId);
                    }

                    // If the watch fails with NoExistException, the collection has been deleted.
                    Futures.addCallback(listTracker.watchFuture,
                            new TrappingCallback<Void>(getErrorReporter()) {
                                @Override
                                public void onFailure(@NonNull Throwable t) {
                                    if (t instanceof NoExistException) {
                                        // (this is idempotent)
                                        trap(getUserCollection().delete(getVContext(), listId));
                                    } else {
                                        super.onFailure(t);
                                    }
                                }
                            });
                }
                return null;
            }
        }));
    }

    @Override
    public void addTodoList(final ListSpec listSpec) {
        final String listName = LISTS_PREFIX + mIdGenerator.generateTailId();
        final Collection listCollection = getDatabase().getCollection(getVContext(), listName);

        // TODO(alexfandrianto): Syncgroup creation is slow if you specify a cloud and are offline.
        // We'll try to connect to the cloud and then time out our RPC. The error is swallowed, so
        // we'll just think there's a blank space of time. Maybe we should just write to these
        // collections anyway. If https://github.com/vanadium/issues/issues/1326 is done, however,
        // we won't need to change this code.
        Futures.addCallback(listCollection.create(getVContext(), null),
                new TrappingCallback<Void>(getErrorReporter()) {
                    @Override
                    public void onSuccess(@Nullable Void result) {
                        Futures.addCallback(createListSyncgroup(listCollection.id()),
                                new TrappingCallback<Void>(getErrorReporter()) {
                                    @Override
                                    public void onSuccess(@Nullable Void result) {
                                        // These can happen in either order.
                                        trap(getUserCollection().put(getVContext(), listName, null,
                                                VdlAny.class));
                                        trap(listCollection.put(getVContext(),
                                                SyncbaseTodoList.LIST_METADATA_ROW_NAME, listSpec,
                                                ListSpec.class));
                                    }
                                });
                    }
                });
    }

    private ListenableFuture<SyncgroupSpec> joinListSyncgroup(String listId) {
        SyncgroupMemberInfo memberInfo = getDefaultMemberInfo();
        String sgName = computeListSyncgroupName(listId);
        String blessingStr = getPersonalBlessingsString(getVContext());
        return getDatabase().getSyncgroup(new Id(blessingStr, sgName)).join(getVContext(),
                CLOUD_NAME, CLOUD_BLESSING, memberInfo);
    }

    private ListenableFuture<Void> createListSyncgroup(Id id) {
        String listId = id.getName();
        final String sgName = computeListSyncgroupName(listId);
        Permissions permissions =
                computePermissionsFromBlessings(getPersonalBlessings(getVContext()));

        SyncgroupMemberInfo memberInfo = getDefaultMemberInfo();

        SyncgroupSpec spec = new SyncgroupSpec(
                "TODOs User Data Collection", CLOUD_NAME, permissions,
                ImmutableList.of(new CollectionRow(id, "")),
                ImmutableList.of(MOUNTPOINT), false);
        String blessingStr = getPersonalBlessingsString(getVContext());
        return getDatabase().getSyncgroup(new Id(blessingStr, sgName)).create(getVContext(),
                spec, memberInfo);
    }

    @Override
    public void deleteTodoList(String key) {
        MainListTracker tracker = mTaskTrackers.remove(key);
        if (tracker != null) {
            trap(tracker.collection.destroy(getVContext()));
        }
    }

    @Override
    public void setCompletion(ListMetadata listMetadata, final boolean done) {
        final String listId = listMetadata.key;
        trap(Batch.runInBatch(getVContext(), getDatabase(), new BatchOptions(),
                new Batch.BatchOperation() {
                    @Override
                    public ListenableFuture<Void> run(final BatchDatabase db) {
                        return sExecutor.submit(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                final Collection list = db.getCollection(getVContext(), listId);

                                InputChannel<KeyValue> scan = list.scan(getVContext(),
                                        RowRange.prefix(SyncbaseTodoList.TASKS_PREFIX));

                                List<ListenableFuture<Void>> puts = new ArrayList<>();

                                for (KeyValue kv : InputChannels.asIterable(scan)) {
                                    TaskSpec taskSpec = castFromSyncbase(kv.getValue().getElem(),
                                            TaskSpec.class);
                                    if (taskSpec.getDone() != done) {
                                        taskSpec.setDone(done);
                                        puts.add(list.put(getVContext(), kv.getKey(), taskSpec,
                                                TaskSpec.class));
                                    }
                                }

                                if (!puts.isEmpty()) {
                                    puts.add(SyncbaseTodoList.updateListTimestamp(
                                            getVContext(), list));
                                }
                                VFutures.sync(Futures.allAsList(puts));
                                return null;
                            }
                        });
                    }
                }));
    }
}
