// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.sharing;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.v.android.v23.V;
import io.v.todos.R;
import io.v.todos.persistence.syncbase.SyncbasePersistence;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.context.VContext;
import io.v.v23.discovery.Advertisement;
import io.v.v23.discovery.Discovery;
import io.v.v23.discovery.Update;
import io.v.v23.security.BlessingPattern;
import io.v.v23.services.syncbase.Id;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Database;
import io.v.v23.syncbase.Invite;
import io.v.v23.syncbase.Syncbase;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.verror.VException;

public final class Sharing {
    private Sharing() {
    }

    private static final String TAG = "SHARING";
    private static final Object sDiscoveryMutex = new Object();
    private static Discovery sDiscovery;
    private static VContext sScanContext;

    public static Discovery getDiscovery() {
        return sDiscovery;
    }

    public static void initDiscovery(Database db) throws VException {
        synchronized (sDiscoveryMutex) {
            if (sDiscovery == null) {
                sDiscovery = V.newDiscovery(SyncbasePersistence.getAppVContext());

                // Rely on the neighborhood fragment to initialize presence advertisement.
                NeighborhoodFragment.initSharePresence();

                sScanContext = initScanForInvites(db);
            }
        }
    }

    // TODO(alexfandrianto): Nobody calls this, so we never stop sharing.
    public static void stopDiscovery() {
        synchronized (sDiscoveryMutex) {
            sScanContext.cancel();
        }
    }

    private static String getRootInterface() {
        return SyncbasePersistence.getAppContext().getPackageName();
    }

    // TODO(alexfandrianto): Make this "presence" and "invitation" once everyone migrates over.
    public static String getPresenceInterface() {
        return getRootInterface() + ".presence2";
    }

    /**
     * Starts a scanner seeking advertisements that invite this user to a todo list. When an invite
     * is found, the app will automatically accept it.
     */
    public static VContext initScanForInvites(Database db)
            throws VException {
        VContext vContext = SyncbasePersistence.getAppVContext().withCancel();
        try {
            db.listenForInvites(vContext, new Database.InviteHandler() {
                @Override
                public void handleInvite(Invite invite) {
                    String prefix = SyncbasePersistence.LIST_COLLECTION_SYNCGROUP_PREFIX +
                        SyncbasePersistence.LISTS_PREFIX;
                    String name = invite.getSyncgroupId().getName();
                    if (!name.startsWith(prefix)) {
                        // Not actually a Todo List.
                        return;
                    }
                    Log.d(TAG, "Accepting todo list invite: " + invite.getSyncgroupId().toString());
                    String blessing = invite.getSyncgroupId().getBlessing();
                    Id listId = new Id(blessing, name.substring(
                        SyncbasePersistence.LIST_COLLECTION_SYNCGROUP_PREFIX.length()));
                    SyncbasePersistence.acceptSharedTodoList(listId);
                }
            });
        } catch (VException e) {
            handleScanListsError(e);
        }
        return vContext;
    }

    private static void handleScanListsError(Throwable t) {
        SyncbasePersistence.getAppErrorReporter().onError(R.string.err_scan_lists, t);
    }
}
