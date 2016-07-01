// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.v.syncbase.Collection;
import io.v.syncbase.Database;
import io.v.syncbase.Syncbase;
import io.v.syncbase.Id;

import io.v.syncbase.Syncgroup;
import io.v.syncbase.SyncgroupInvite;
import io.v.syncbase.WatchChange;
import io.v.syncbase.core.VError;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.Persistence;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.sharing.NeighborhoodFragment;
import io.v.todos.sharing.ShareListDialogFragment;

public abstract class SyncbasePersistence implements Persistence {
    protected static final String SETTINGS_COLLECTION = "settings";
    protected static final String SHOW_DONE_KEY = "showDoneKey";
    protected static final String TODO_LIST_KEY = "todoListKey";
    protected static final String TAG = "High-Level Syncbase";

    protected static boolean sInitialized = false;

    protected static final Map<Id, ListSpec> sListSpecMap = new HashMap<>();
    protected static final Map<Id, ListMetadataTracker> sListMetadataTrackerMap = new HashMap<>();
    protected static final Map<Id, Map<String, TaskSpec>> sTasksByListMap = new HashMap<>();
    protected static boolean sShowDone = true;

    protected static Database sDb;
    protected static Collection sSettings;

    private static final Object sSyncbaseMutex = new Object();
    private static TodoListListener sTodoListListener;
    private static Id sTodoListExpectedId;
    private static ListEventListener<ListMetadata> sMainListener;

    SyncbasePersistence(final Activity activity, Bundle savedInstanceState) {
        Log.d(TAG, "Trying to start Syncbase Persistence...");
        /**
         * Initializes Syncbase Server
         * Starts up a watch stream to watch all the data with methods to access/modify the data.
         * This watch stream will also allow us to "watch" who has been shared to, if we desire.
         * Starts up an invite handler to automatically accept invitations.
         */
        synchronized (sSyncbaseMutex) {
            if (!sInitialized) {
                Log.d(TAG, "Initializing Syncbase Persistence...");

                Syncbase.Options opts = new Syncbase.Options();
                opts.rootDir = activity.getFilesDir().getAbsolutePath();
                opts.disableSyncgroupPublishing = true;
                // TODO(alexfandrianto): https://v.io/i/1375
                opts.disableUserdataSyncgroup = true;
                try {
                    Syncbase.init(opts);
                } catch (VError vError) {
                    Log.e(TAG, "Failed to initialize", vError);
                    return;
                }

                final Object initializeMutex = new Object();

                Log.d(TAG, "Logging the user in!");
                if (!Syncbase.isLoggedIn()) {
                    Syncbase.loginAndroid(activity, new Syncbase.LoginCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Successfully logged in!");
                            try {
                                sDb = Syncbase.database();
                            } catch (VError vError) {
                                Log.e(TAG, "Failed to create database", vError);
                                callNotify();
                                return;
                            }
                            continueSetup();
                            sInitialized = true;
                            Log.d(TAG, "Successfully initialized!");
                            callNotify();
                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.e(TAG, "Failed to login. :(", e);
                            callNotify();
                        }

                        private void callNotify() {
                            synchronized (initializeMutex) {
                                initializeMutex.notify();
                            }
                        }
                    });
                }

                Log.d(TAG, "Let's wait until we are logged in...");
                synchronized (initializeMutex) {
                    try {
                        initializeMutex.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "could not wait for initialization to finish", e);
                    }
                }

                if (sInitialized) {
                    Log.d(TAG, "Syncbase Persistence initialization complete!");
                } else {
                    Log.d(TAG, "Syncbase Persistence initialization FAILED!");
                    return;
                }
            }
        }

        // Prepare the share presence menu fragment.
        FragmentManager mgr = activity.getFragmentManager();
        if (savedInstanceState == null) {
            FragmentTransaction t = mgr.beginTransaction();
            addFeatureFragments(mgr, activity, t);
            t.commit();
        } else {
            addFeatureFragments(mgr, activity, null);
        }
    }

    /**
     * Hook to insert or rebind fragments.
     *
     * @param manager
     * @param transaction the fragment transaction to use to add fragments, or null if fragments are
     *                    being restored by the system.
     */
    @CallSuper
    protected void addFeatureFragments(FragmentManager manager, Context context,
                                       @Nullable FragmentTransaction transaction) {
        if (transaction != null) {
            NeighborhoodFragment fragment = new NeighborhoodFragment();
            fragment.initSharePresence(context);
            transaction.add(fragment, NeighborhoodFragment.FRAGMENT_TAG);
        }
    }

    private void continueSetup() {
        Log.d(TAG, "Creating settings collection");
        // Create a settings collection.
        try {
            sSettings = sDb.collection(SETTINGS_COLLECTION);
        } catch (VError vError) {
            Log.e(TAG, "couldn't create settings collection", vError);
        }

        Log.d(TAG, "Watching everything");
        // Watch everything.
        // TODO(alexfandrianto): This can be simplified if we watch specific collections and the
        // entrance/exit of collections. https://v.io/i/1376
        sDb.addWatchChangeHandler(new Database.WatchChangeHandler() {
            @Override
            public void onInitialState(final Iterator<WatchChange> values) {
                while (values.hasNext()) {
                    handlePutChange(values.next());
                }
            }

            @Override
            public void onChangeBatch(final Iterator<WatchChange> changes) {
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
                Log.d(TAG, "Handling put change " + value.getRowKey());
                Log.d(TAG, "From collection: " + value.getCollectionId());
                Log.d(TAG, "With entity type: " + value.getEntityType());
                if (value.getEntityType() != WatchChange.EntityType.ROW ||
                        value.getCollectionId().getName().equals("userdata__")) {
                    // TODO(alexfandrianto): I can't deal with these yet. Please skip to avoid crashing.
                    // TODO(alexfandrianto): export/hide userdata__ https://v.io/i/1372
                    return;
                }
                Log.d(TAG, "With row...: " + value.getRowKey());
                final Id collectionId = value.getCollectionId();

                if (collectionId.getName().equals(SETTINGS_COLLECTION)) {
                    if (value.getRowKey().equals(SHOW_DONE_KEY)) {
                        try {
                            sShowDone = value.getValue(Boolean.class);
                            Log.d(TAG, "Got a show done" + sShowDone);

                            // Inform the relevant listener.
                            if (sTodoListListener != null) {
                                sTodoListListener.onUpdateShowDone(sShowDone);
                            }
                        } catch (VError vError) {
                            Log.e(TAG, "Failed to decode watch change as Boolean", vError);
                        }
                    }
                    return; // Show done updated. Nothing left to do.
                }

                // If we are here, we must be modifying a todo list collection.
                // Initialize the task spec map, if necessary.
                if (sTasksByListMap.get(collectionId) == null) {
                    sTasksByListMap.put(collectionId, new HashMap<String, TaskSpec>());
                }

                if (value.getRowKey().equals(TODO_LIST_KEY)) {
                    try {
                        final ListSpec listSpec = value.getValue(ListSpec.class);
                        Log.d(TAG, "Got a list" + listSpec.toString());
                        sListSpecMap.put(collectionId, listSpec);

                        final ListMetadataTracker tracker = getListMetadataTrackerSafe(collectionId);
                        tracker.setSpec(listSpec);

                        // Inform the relevant listeners.
                        if (sMainListener != null) {
                            tracker.fireListener(sMainListener);
                        }
                        if (sTodoListListener != null && sTodoListExpectedId.equals(collectionId)) {
                            sTodoListListener.onUpdate(listSpec);
                        }
                    } catch (VError vError) {
                        Log.e(TAG, "Failed to decode watch change value as ListSpec", vError);
                    }
                } else {
                    Map<String, TaskSpec> taskData = sTasksByListMap.get(collectionId);
                    final String rowKey = value.getRowKey();
                    try {
                        final TaskSpec newSpec = value.getValue(TaskSpec.class);
                        Log.d(TAG, "Got a task" + newSpec.toString());
                        final TaskSpec oldSpec = taskData.put(rowKey, newSpec);

                        final ListMetadataTracker tracker = getListMetadataTrackerSafe(collectionId);
                        tracker.adjustTask(rowKey, newSpec.getDone());

                        // Inform the relevant listeners.
                        if (sMainListener != null) {
                            tracker.fireListener(sMainListener);
                        }
                        if (sTodoListListener != null && sTodoListExpectedId.equals(collectionId)) {
                            if (oldSpec == null) {
                                sTodoListListener.onItemAdd(new Task(rowKey, newSpec));
                            } else {
                                sTodoListListener.onItemUpdate(new Task(rowKey, newSpec));
                            }
                        }
                    } catch (VError vError) {
                        Log.e(TAG, "Failed to decode watch change value as TaskSpec", vError);
                    }
                }
            }

            // TODO(alexfandrianto): This will fire listeners despite the WatchChange's potentially
            // being within a batch. Over-firing the listeners isn't ideal, but the app should be
            // okay.
            private void handleDeleteChange(WatchChange value) {
                Log.d(TAG, "Handling delete change " + value.getRowKey());
                Log.d(TAG, "From collection: " + value.getCollectionId());
                Log.d(TAG, "With entity type: " + value.getEntityType());
                if (value.getEntityType() != WatchChange.EntityType.ROW || value.getCollectionId().getName().equals("userdata__")) {
                    // TODO(alexfandrianto): I can't deal with these yet. Please skip to avoid crashing.
                    // TODO(alexfandrianto): export/hide userdata__ https://v.io/i/1372
                    return;
                }
                Log.d(TAG, "With row...: " + value.getRowKey());

                final Id collectionId = value.getCollectionId();
                final String oldKey = value.getRowKey();
                if (oldKey.equals(TODO_LIST_KEY)) {
                    sListSpecMap.remove(collectionId);
                    sListMetadataTrackerMap.remove(collectionId);

                    // TODO(alexfandrianto): Potentially destroy the collection too?
                    // Inform the relevant listeners.
                    if (sMainListener != null) {
                        sMainListener.onItemDelete(collectionId.encode());
                    }
                    if (sTodoListListener != null && sTodoListExpectedId.equals(collectionId)) {
                        sTodoListListener.onDelete();
                    }
                } else {
                    Map<String, TaskSpec> tasks = sTasksByListMap.get(collectionId);
                    if (tasks != null) {
                        tasks.remove(oldKey);

                        final ListMetadataTracker tracker = getListMetadataTrackerSafe(collectionId);
                        tracker.removeTask(oldKey);

                        // Inform the relevant listeners.
                        if (sMainListener != null) {
                            tracker.fireListener(sMainListener);
                        }
                        if (sTodoListListener != null && sTodoListExpectedId.equals(collectionId)) {
                            sTodoListListener.onItemDelete(oldKey);
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
        sDb.addSyncgroupInviteHandler(new Database.SyncgroupInviteHandler() {
            @Override
            public void onInvite(SyncgroupInvite invite) {
                sDb.acceptSyncgroupInvite(invite, new Database.AcceptSyncgroupInviteCallback() {
                    @Override
                    public void onSuccess(Syncgroup sg) {
                        Log.d(TAG, "Successfully joined syncgroup: " + sg.getId().toString());
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        Log.w(TAG, "Failed to accept invitation", e);
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                Log.w(TAG, "error while handling invitations", e);
            }
        }, new Database.AddSyncgroupInviteHandlerOptions());

        // And do a background scan for peers near me.
        ShareListDialogFragment.initScan();
    }

    public static boolean isInitialized() {
        return sInitialized;
    }

    @Override
    public abstract void close();

    @Override
    public String debugDetails() {
        return null;
    }

    protected void setMainListener(ListEventListener<ListMetadata> listener) {
        sMainListener = listener;
    }
    protected void removeMainListener() {
        sMainListener = null;
    }

    protected void setTodoListListener(TodoListListener listener, Id expectedId) {
        sTodoListListener = listener;
        sTodoListExpectedId = expectedId;
    }
    protected void removeTodoListListener() {
        sTodoListListener = null;
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
            if (spec == null) {
                return null;
            }
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
            ListMetadata metadata = computeListMetadata();
            if (metadata == null) {
                return; // cannot fire yet
            }
            if (!hasFired) {
                hasFired = true;
                listener.onItemAdd(computeListMetadata());
            } else {
                listener.onItemUpdate(computeListMetadata());
            }
        }
    }
}
