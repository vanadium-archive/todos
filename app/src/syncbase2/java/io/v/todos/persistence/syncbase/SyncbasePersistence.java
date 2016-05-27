// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;

import java.util.Iterator;

import io.v.syncbase.Database;
import io.v.syncbase.Syncbase;

import io.v.syncbase.SyncgroupInvite;
import io.v.syncbase.WatchChange;
import io.v.todos.persistence.Persistence;

public class SyncbasePersistence implements Persistence {
    protected static boolean sInitialized = false;
    protected static Database sDb;

    public SyncbasePersistence(Activity activity, Bundle savedInstanceState) {
        /**
         * Initializes Syncbase Server
         * Starts up a watch stream to watch all the data with methods to access/modify the data.
         * This watch stream will also allow us to "watch" who has been shared to, if we desire.
         * Starts up an invite handler to automatically accept invitations.
         */
        Syncbase.DatabaseOptions dbOpts = new Syncbase.DatabaseOptions();
        dbOpts.rootDir = activity.getFilesDir().getAbsolutePath();

        // Start Syncbase Server
        // sDb = Syncbase.database(dbOpts); // TODO(alexfandrianto): This will crash though.

        // Watch everything.
        sDb.addWatchChangeHandler(new Database.WatchChangeHandler() {
            @Override
            public void onInitialState(Iterator<WatchChange> values) {

            }

            @Override
            public void onChangeBatch(Iterator<WatchChange> changes) {
            }

            @Override
            public void onError(Throwable e) {
            }
        }, new Database.AddWatchChangeHandlerOptions());


        // Automatically accept invitations.
        sDb.addSyncgroupInviteHandler(new Database.SyncgroupInviteHandler() {
            @Override
            public void onInvite(SyncgroupInvite invite) {
            }

            @Override
            public void onError(Throwable e) {
            }
        }, new Database.AddSyncgroupInviteHandlerOptions());

        sInitialized = true;
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
}
