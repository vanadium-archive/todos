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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;
import io.v.v23.InputChannelCallback;
import io.v.v23.security.access.Permissions;
import io.v.v23.services.syncbase.Id;
import io.v.v23.services.syncbase.SyncgroupJoinFailedException;
import io.v.v23.services.syncbase.SyncgroupMemberInfo;
import io.v.v23.services.syncbase.SyncgroupSpec;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.verror.NoExistException;
import io.v.v23.verror.VException;

public class SyncbaseMain extends SyncbasePersistence implements MainPersistence {
    private static final String
            TAG = SyncbaseMain.class.getSimpleName();

    private static final int DEFAULT_MAX_JOIN_ATTEMPTS = 15;
    private static final long RETRY_DELAY = 2000;

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
        trap(watchUserCollection(new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange change) {
                try {
                    final String listIdStr = change.getRowName();
                    final Id listId = convertStringToId(listIdStr);

                    if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
                        // (this is idempotent)
                        Log.d(TAG, listIdStr + " removed from index");
                        deleteTodoList(listIdStr);
                    } else {
                        // If we are tracking this list already, don't bother doing anything.
                        // This might happen if a same-user device did a simultaneous put into the
                        // userdata collection.
                        if (mTaskTrackers.get(listIdStr) != null) {
                            return null;
                        }

                        mIdGenerator.registerId(listId.getName().substring(LISTS_PREFIX.length()));

                        Log.d(TAG, "Found a list id from userdata watch: " + listId.getName() +
                                " with owner: " + listId.getBlessing());
                        trap(joinWithRetry(listId));

                        MainListTracker listTracker = new MainListTracker(getVContext(),
                                getDatabase(), listId, listener);
                        mTaskTrackers.put(listIdStr, listTracker);

                        // If the watch fails with NoExistException, the collection has been deleted.
                        Futures.addCallback(listTracker.watchFuture, new SyncTrappingCallback<Void>() {
                            @Override
                            public void onFailure(@NonNull Throwable t) {
                                if (t instanceof NoExistException) {
                                    // (this is idempotent)
                                    trap(getUserCollection().delete(getVContext(), listIdStr));
                                } else {
                                    super.onFailure(t);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error during watch handle", e);
                }
                return null;
            }
        }));
    }

    @Override
    public String addTodoList(final ListSpec listSpec) {
        final String listName = LISTS_PREFIX + mIdGenerator.generateTailId();
        final Id listId = new Id(getPersonalBlessingsString(), listName);
        final Collection listCollection = getDatabase().getCollection(listId);

        Futures.addCallback(listCollection.create(getVContext(), null),
                new SyncTrappingCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void result) {
                        // These can happen in any order.
                        trap(listCollection.put(getVContext(),
                                SyncbaseTodoList.LIST_METADATA_ROW_NAME, listSpec));
                        trap(rememberTodoList(listId));
                        // TODO(alexfandrianto): Syncgroup creation is slow if you specify a cloud
                        // and are offline. https://github.com/vanadium/issues/issues/1326
                        trap(createListSyncgroup(listCollection.id()));
                    }
                });
        return convertIdToString(listId);
    }

    private ListenableFuture<SyncgroupSpec> joinListSyncgroup(Id listId) {
        SyncgroupMemberInfo memberInfo = getDefaultMemberInfo();
        String sgName = computeListSyncgroupName(listId.getName());
        return getDatabase().getSyncgroup(new Id(listId.getBlessing(), sgName)).join(getVContext(),
                CLOUD_NAME, Arrays.asList(CLOUD_BLESSING), memberInfo);
    }

    // Join the syncgroup. Retry if there are failures.
    private ListenableFuture<SyncgroupSpec> joinWithRetry(Id listId) {
        return joinWithRetry(listId, 0, DEFAULT_MAX_JOIN_ATTEMPTS);
    }

    private ListenableFuture<SyncgroupSpec> joinWithRetry(final Id listId, final int numTimes,
                                                          final int limit) {
        final String debugString = (numTimes + 1) + "/" + limit + " for: " + listId;
        Log.d(TAG, "Join attempt " + debugString);
        if (numTimes + 1 == limit) { // final attempt!
            return joinListSyncgroup(listId);
        }
        // Note: This can be easily converted to exponential backoff.
        final long delay = RETRY_DELAY;
        return Futures.catchingAsync(
                joinListSyncgroup(listId),
                SyncgroupJoinFailedException.class,
                new AsyncFunction<SyncgroupJoinFailedException, SyncgroupSpec>() {
                    public ListenableFuture<SyncgroupSpec> apply(@Nullable
                                                                 SyncgroupJoinFailedException
                                                                         input) {
                        Log.d(TAG, "Join failed. Sleeping " + debugString + " with delay " + delay);
                        return sExecutor.schedule(new Callable<SyncgroupSpec>() {


                            @Override
                            public SyncgroupSpec call() {
                                Log.d(TAG, "Sleep done. Retry " + debugString);

                                // If this errors, then we will not get another chance to
                                // see this syncgroup until the app is restarted.
                                try {
                                    return joinWithRetry(listId, numTimes + 1, limit).get();
                                } catch (InterruptedException | ExecutionException e) {
                                    return null;
                                }
                            }
                        }, delay, TimeUnit.MILLISECONDS);
                    }
                });
    }

    private ListenableFuture<Void> createListSyncgroup(Id id) {
        String listName = id.getName();
        final String sgName = computeListSyncgroupName(listName);
        Permissions permissions =
                computePermissionsFromBlessings(getPersonalBlessings());

        SyncgroupMemberInfo memberInfo = getDefaultMemberInfo();

        SyncgroupSpec spec = new SyncgroupSpec(
                "TODO list", CLOUD_NAME, permissions,
                ImmutableList.of(id),
                ImmutableList.of(MOUNTPOINT), false);
        String blessingStr = getPersonalBlessingsString();
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
}
