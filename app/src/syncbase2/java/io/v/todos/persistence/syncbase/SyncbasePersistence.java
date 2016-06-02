// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.v.android.VAndroidContexts;
import io.v.android.security.BlessingsManager;
import io.v.syncbase.Collection;
import io.v.syncbase.Database;
import io.v.syncbase.Syncbase;
import io.v.syncbase.Id;

import io.v.syncbase.WatchChange;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.Persistence;
import io.v.todos.persistence.TodoListListener;
import io.v.v23.VFutures;
import io.v.v23.context.VContext;
import io.v.v23.security.Blessings;
import io.v.v23.verror.VException;

public class SyncbasePersistence implements Persistence {
    protected static final String SETTINGS_COLLECTION = "settings";
    protected static final String SHOW_DONE_KEY = "showDoneKey";
    protected static final String TODO_LIST_KEY = "todoListKey";
    protected static final String TAG = "High-Level Syncbase";

    private static final String BLESSINGS_KEY = "blessings";

    protected static boolean sInitialized = false;

    protected static final Map<Id, ListSpec> sListSpecMap = new HashMap<>();
    protected static final Map<Id, ListMetadataTracker> sListMetadataTrackerMap = new HashMap<>();
    protected static final Map<Id, Map<String, TaskSpec>> sTasksByListMap = new HashMap<>();
    protected static boolean sShowDone = true;

    protected Database mDb;
    protected Collection mSettings;

    private static final Object sSyncbaseMutex = new Object();
    private TodoListListener mTodoListListener;
    private ListEventListener<ListMetadata> mMainListener;

    public SyncbasePersistence(final Activity activity, Bundle savedInstanceState) {
        /**
         * Initializes Syncbase Server
         * Starts up a watch stream to watch all the data with methods to access/modify the data.
         * This watch stream will also allow us to "watch" who has been shared to, if we desire.
         * Starts up an invite handler to automatically accept invitations.
         */
        synchronized (sSyncbaseMutex) {
            if (!sInitialized) {
                Log.d(TAG, "Initializing Syncbase Persistence...");

                Syncbase.DatabaseOptions dbOpts = new Syncbase.DatabaseOptions();
                dbOpts.rootDir = activity.getFilesDir().getAbsolutePath();
                dbOpts.disableUserdataSyncgroup = true;
                dbOpts.vContext = VAndroidContexts.withDefaults(activity,
                        savedInstanceState).getVContext();

                final VContext vContext = dbOpts.vContext;

                Log.d(TAG, "Done getting vanadium context!");

                final SettableFuture<ListenableFuture<Blessings>> blessings =
                        SettableFuture.create();
                if (activity.getMainLooper().getThread() == Thread.currentThread()) {
                    blessings.set(BlessingsManager.getBlessings(vContext, activity,
                            BLESSINGS_KEY, true));
                } else {
                    new Handler(activity.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            blessings.set(BlessingsManager.getBlessings(vContext,
                                    activity, BLESSINGS_KEY, true));
                        }
                    });
                }

                try {
                    VFutures.sync(Futures.dereference(blessings));
                } catch (VException e) {
                    Log.e(TAG, "Failed to get blessings", e);
                }

                Log.d(TAG, "Done getting blessings!");

                final Object initializeMutex = new Object();

                Syncbase.database(new Syncbase.DatabaseCallback() {
                    @Override
                    public void onSuccess(Database db) {
                        super.onSuccess(db);
                        Log.d(TAG, "Got a db handle!");
                        mDb = db;
                        continueSetup();
                        sInitialized = true;
                        Log.d(TAG, "Successfully initialized!");
                        synchronized (initializeMutex) {
                            initializeMutex.notify();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);

                        Log.e(TAG, "Failed to get database handle", e);
                        synchronized (initializeMutex) {
                            initializeMutex.notify();
                        }
                    }
                }, dbOpts);

                Log.d(TAG, "Let's wait until the database is ready...");
                synchronized (initializeMutex) {
                    try {
                        initializeMutex.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "could not wait for initialization to finish", e);
                    }
                }

                Log.d(TAG, "Syncbase Persistence initialization complete!");
            }
        }
    }

    private void continueSetup() {
        Log.d(TAG, "Creating settings collection");
        // Create a settings collection.
        mSettings = mDb.collection(SETTINGS_COLLECTION);

        Log.d(TAG, "Watching everything");
        // Watch everything.
        mDb.addWatchChangeHandler(new Database.WatchChangeHandler() {
            @Override
            public void onInitialState(Iterator<WatchChange> values) {
                while (values.hasNext()) {
                    handlePutChange(values.next());
                }
            }

            @Override
            public void onChangeBatch(Iterator<WatchChange> changes) {
                while (changes.hasNext()) {
                    WatchChange change = changes.next();
                    if (change.getChangeType() == WatchChange.ChangeType.DELETE) {
                        handleDeleteChange(change);
                    } else {
                        handlePutChange(change);
                    }
                }
            }

            // TODO(alexfandrianto): This will fire listeners despite the WatchChange's potentially
            // being within a batch. Over-firing the listeners isn't ideal, but the app should be
            // okay.
            private void handlePutChange(WatchChange value) {
                Id collectionId = value.getCollectionId();

                if (collectionId.getName().equals(SETTINGS_COLLECTION)) {
                    if (value.getRowKey().equals(SHOW_DONE_KEY)) {
                        sShowDone = (Boolean)value.getValue();

                        // Inform the relevant listener.
                        if (mTodoListListener != null) {
                            mTodoListListener.onUpdateShowDone(sShowDone);
                        }
                    }
                }

                // Initialize the task spec map, if necessary.
                if (sTasksByListMap.get(collectionId) == null) {
                    sTasksByListMap.put(collectionId, new HashMap<String, TaskSpec>());
                }

                if (value.getRowKey().equals(TODO_LIST_KEY)) {
                    ListSpec listSpec = (ListSpec) value.getValue();
                    sListSpecMap.put(collectionId, listSpec);

                    ListMetadataTracker tracker = getListMetadataTrackerSafe(collectionId);
                    tracker.setSpec(listSpec);

                    // Inform the relevant listeners.
                    if (mMainListener != null) {
                        tracker.fireListener(mMainListener);
                    }
                    if (mTodoListListener != null) {
                        mTodoListListener.onUpdate(listSpec);
                    }
                } else {
                    Map<String, TaskSpec> taskData = sTasksByListMap.get(collectionId);
                    TaskSpec newSpec = (TaskSpec)value.getValue();
                    TaskSpec oldSpec = taskData.put(value.getRowKey(), newSpec);

                    ListMetadataTracker tracker = getListMetadataTrackerSafe(collectionId);
                    tracker.adjustTask(value.getRowKey(), newSpec.getDone());

                    // Inform the relevant listeners.
                    if (mMainListener != null) {
                        tracker.fireListener(mMainListener);
                    }
                    if (mTodoListListener != null) {
                        if (oldSpec == null) {
                            mTodoListListener.onItemAdd(new Task(value.getRowKey(), newSpec));
                        } else {
                            mTodoListListener.onItemUpdate(new Task(value.getRowKey(), newSpec));
                        }
                    }
                }
            }

            // TODO(alexfandrianto): This will fire listeners despite the WatchChange's potentially
            // being within a batch. Over-firing the listeners isn't ideal, but the app should be
            // okay.
            private void handleDeleteChange(WatchChange value) {
                Id collectionId = value.getCollectionId();
                String oldKey = value.getRowKey();
                if (oldKey.equals(TODO_LIST_KEY)) {
                    sListSpecMap.remove(collectionId);
                    sListMetadataTrackerMap.remove(collectionId);

                    // TODO(alexfandrianto): Potentially destroy the collection too?

                    // Inform the relevant listeners.
                    if (mMainListener != null) {
                        mMainListener.onItemDelete(oldKey);
                    }
                    if (mTodoListListener != null) {
                        mTodoListListener.onDelete();
                    }
                } else {
                    Map<String, TaskSpec> tasks = sTasksByListMap.get(collectionId);
                    if (tasks != null) {
                        tasks.remove(oldKey);

                        ListMetadataTracker tracker = getListMetadataTrackerSafe(collectionId);
                        tracker.removeTask(oldKey);

                        // Inform the relevant listeners.
                        if (mMainListener != null) {
                            tracker.fireListener(mMainListener);
                        }
                        if (mTodoListListener != null) {
                            mTodoListListener.onItemDelete(value.getRowKey());
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable e) {
                Log.w(TAG, "error during watch", e);
            }
        }, new Database.AddWatchChangeHandlerOptions());

        Log.d(TAG, "Accepting all invitations");

        // Automatically accept invitations.
        // TODO(alexfandrianto): Uncomment. This part of the high-level API isn't implemented yet.
        /*mDb.addSyncgroupInviteHandler(new Database.SyncgroupInviteHandler() {
            @Override
            public void onInvite(SyncgroupInvite invite) {
                mDb.acceptSyncgroupInvite(invite, new Database.AcceptSyncgroupInviteCallback() {
                    @Override
                    public void onSuccess(Syncgroup sg) {
                        super.onSuccess(sg);
                        Log.d(TAG, "Successfully joined syncgroup: " + sg.getId().toString());
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        super.onFailure(e);
                        Log.w(TAG, "Failed to accept invitation", e);
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                Log.w(TAG, "error while handling invitations", e);
            }
        }, new Database.AddSyncgroupInviteHandlerOptions());*/
    }

    public static boolean isInitialized() {
        return sInitialized;
    }

    @Override
    public void close() {

    }

    @Override
    public String debugDetails() {
        return null;
    }

    protected void setMainListener(ListEventListener<ListMetadata> listener) {
        mMainListener = listener;
    }
    protected void removeMainListener() {
        mMainListener = null;
    }

    protected void setTodoListListener(TodoListListener listener) {
        mTodoListListener = listener;
    }
    protected void removeTodoListListener() {
        mTodoListListener = null;
    }

    private ListMetadataTracker getListMetadataTrackerSafe(Id listId) {
        ListMetadataTracker tracker = sListMetadataTrackerMap.get(listId);
        if (tracker == null) {
            tracker = new ListMetadataTracker(listId);
            sListMetadataTrackerMap.put(listId, tracker);
        }
        return tracker;
    }

    class ListMetadataTracker {
        private final Id collectionId;
        private ListSpec spec;
        private int numCompleted = 0;
        private Map<String, Boolean> taskCompletion = new HashMap<>();
        private boolean hasFired;

        ListMetadataTracker(Id collectionId) {
            this.collectionId = collectionId;
        }

        ListMetadata computeListMetadata() {
            return new ListMetadata(collectionId.encode(), spec, numCompleted,
                    taskCompletion.size());
        }

        void setSpec(ListSpec newSpec) {
            spec = newSpec;
        }

        void adjustTask(String taskKey, boolean done) {
            Boolean oldDone = taskCompletion.put(taskKey, done);
            if ((oldDone == null || !oldDone) && done) {
                numCompleted++;
            } else if (oldDone != null && oldDone && !done) {
                numCompleted--;
            }
        }

        void removeTask(String taskKey) {
            Boolean oldDone = taskCompletion.remove(taskKey);
            if (oldDone != null && oldDone) {
                numCompleted--;
            }
        }

        void fireListener(ListEventListener<ListMetadata> listener) {
            if (!hasFired) {
                listener.onItemAdd(computeListMetadata());
            } else {
                hasFired = true;
                listener.onItemUpdate(computeListMetadata());
            }
        }
    }
}
