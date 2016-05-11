// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.sharing;

import io.v.android.v23.V;
import io.v.todos.persistence.syncbase.SyncbasePersistence;
import io.v.v23.discovery.Discovery;
import io.v.v23.verror.VException;

public final class Sharing {
    private Sharing(){}

    private static final Object sDiscoveryMutex = new Object();
    private static Discovery sDiscovery;

    public static Discovery getDiscovery() {
        return sDiscovery;
    }

    public static void initDiscovery() throws VException {
        synchronized (sDiscoveryMutex) {
            if (sDiscovery == null) {
                sDiscovery = V.newDiscovery(SyncbasePersistence.getAppVContext());

                // Rely on the neighborhood fragment to initialize presence advertisement.
                NeighborhoodFragment.initSharePresence();
            }
        }
    }

    private static String getRootInterface() {
        return SyncbasePersistence.getAppContext().getPackageName();
    }

    public static String getPresenceInterface() {
        return getRootInterface() + ".presence";
    }

    public static String getInvitationInterface() {
        return getRootInterface() + ".invitation";
    }
}
