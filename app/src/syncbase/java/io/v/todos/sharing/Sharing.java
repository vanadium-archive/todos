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
import io.v.v23.syncbase.Syncbase;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.verror.VException;

public final class Sharing {
    private Sharing() {
    }

    private static final String TAG = "SHARING";
    private static final String OWNER_KEY = "owner";
    private static final Object sDiscoveryMutex = new Object();
    private static Discovery sDiscovery;
    private static VContext sScanContext;
    private static VContext sAdvertiseContext;
    private final static Map<String, VContext> sAdContextMap = new HashMap<>();

    public static Discovery getDiscovery() {
        return sDiscovery;
    }

    public static void initDiscovery() throws VException {
        synchronized (sDiscoveryMutex) {
            if (sDiscovery == null) {
                sDiscovery = V.newDiscovery(SyncbasePersistence.getAppVContext());

                // Rely on the neighborhood fragment to initialize presence advertisement.
                NeighborhoodFragment.initSharePresence();

                sScanContext = initScanForInvites();
                sAdvertiseContext = initAdvertiseLists();
            }
        }
    }

    // TODO(alexfandrianto): Nobody calls this, so we never stop sharing.
    public static void stopDiscovery() {
        synchronized (sDiscoveryMutex) {
            sScanContext.cancel();
            sAdvertiseContext.cancel();
            sAdContextMap.clear();
        }
    }

    private static String getRootInterface() {
        return SyncbasePersistence.getAppContext().getPackageName();
    }

    // TODO(alexfandrianto): Make this "presence" and "invitation" once everyone migrates over.
    public static String getPresenceInterface() {
        return getRootInterface() + ".presence2";
    }

    public static String getInvitationInterface() {
        return getRootInterface() + ".invitation2";
    }

    /**
     * Starts a scanner seeking advertisements that invite this user to a todo list. When an invite
     * is found, the app will automatically accept it.
     */
    public static VContext initScanForInvites()
            throws VException {
        VContext vContext = SyncbasePersistence.getAppVContext().withCancel();
        try {
            ListenableFuture<Void> scan = InputChannels.withCallback(
                    Sharing.getDiscovery().scan(vContext,
                            "v.InterfaceName = \"" + Sharing.getInvitationInterface() + "\""),
                    new InputChannelCallback<Update>() {
                        @Override
                        public ListenableFuture<Void> onNext(Update result) {
                            final String listName = Iterables.getOnlyElement(result.getAddresses());
                            if (listName == null) {
                                return null;
                            }
                            String owner = result.getAttribute(OWNER_KEY);
                            Log.d("SHARING", "Noticed advertised list: " + listName + " by: " +
                                    owner);

                            // TODO(alexfandrianto): Remove hack.
                            // https://github.com/vanadium/issues/issues/1328
                            if (result.getAttribute(SyncbasePersistence
                                    .getPersonalBlessingsString()) == null) {
                                Log.d(TAG, "...but the ad was not meant for this user.");
                                return null; // ignore; this isn't meant for us
                            }

                            // Never mind about losses, just handle found advertisements.
                            if (!result.isLost()) {
                                Log.d(TAG, "...and will accept it.");

                                SyncbasePersistence.acceptSharedTodoList(new Id(owner, listName));
                            }
                            return null;
                        }
                    });
            Futures.addCallback(scan, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                }

                @Override
                public void onFailure(Throwable t) {
                    handleScanListsError(t);
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

    /**
     * Creates advertisements based on the todo lists this user has created thus far and those that
     * are created in the future. The advertisements will need to be targeted to the users that have
     * been invited to the list.
     *
     * @return
     * @throws VException
     */
    public static VContext initAdvertiseLists()
            throws VException {
        final VContext vContext = SyncbasePersistence.getAppVContext().withCancel();

        // Prepare a watch on top of the userdata collection to determine which todo lists need to
        // be tracked by this application.
        SyncbasePersistence.watchUserCollection(new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange change) {
                try {
                    final String listIdStr = change.getRowName();
                    final Id listId = SyncbasePersistence.convertStringToId(listIdStr);

                    if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
                        VContext ctx = sAdContextMap.remove(listIdStr);
                        if (ctx != null) { // TODO(alexfandrianto): ctx might be null if ad failed?
                            ctx.cancel(); // Stop advertising the list; it's been deleted.
                        }
                    } else {
                        final String owner = listId.getBlessing();
                        if (!owner.equals(SyncbasePersistence.getPersonalBlessingsString())) {
                            return Futures.immediateFuture((Void) null);
                        }

                        // We should probably start to advertise this collection and check its spec.
                        SyncbasePersistence.watchSharedTo(listId, new Function<List<BlessingPattern>,
                                Void>() {
                            @Override
                            public Void apply(List<BlessingPattern> patterns) {
                                // Make a copy of the patterns list that excludes the cloud and this
                                // user's blessings.
                                List<BlessingPattern> filteredPatterns = new ArrayList<>();
                                for (BlessingPattern pattern : patterns) {
                                    String pStr = pattern.toString();
                                    if (pStr.equals(SyncbasePersistence.getPersonalBlessingsString()) ||
                                            pStr.equals(SyncbasePersistence.CLOUD_BLESSING)) {
                                        continue;
                                    }
                                    filteredPatterns.add(pattern);
                                }

                                // Advertise to the remaining patterns.
                                if (filteredPatterns.size() > 0) {
                                    Log.d(TAG, "Must advertise for " + listIdStr + " to " +
                                            filteredPatterns.toString());
                                    advertiseList(vContext, listId, filteredPatterns);
                                }
                                return null;
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error during watch handle", e);
                }
                return null;
            }
        });
        return vContext;
    }

    /**
     * Advertises that this list is available to this set of people. Cancels the old advertisement
     * if one exists. Only called by initAdvertiseLists.
     *
     * @param baseContext The context for all advertisements
     * @param listId      The list to be advertised
     * @param patterns    Blessings that the advertisement should target
     */
    private static void advertiseList(VContext baseContext, Id listId, List<BlessingPattern>
            patterns) {
        if (baseContext.isCanceled()) {
            Log.w(TAG, "Base context was canceled; cannot advertise");
            return;
        }
        // Swap out the ad context...
        String key = SyncbasePersistence.convertIdToString(listId);
        VContext oldAdContext = sAdContextMap.remove(key);
        if (oldAdContext != null) {
            oldAdContext.cancel();
        }
        VContext newAdContext = baseContext.withCancel();
        sAdContextMap.put(key, newAdContext);


        try {
            Advertisement ad = new Advertisement();
            ad.setInterfaceName(Sharing.getInvitationInterface());
            ad.getAddresses().add(listId.getName());
            ad.getAttributes().put(OWNER_KEY, listId.getBlessing());

            // TODO(alexfandrianto): Remove hack. https://github.com/vanadium/issues/issues/1328
            for (BlessingPattern pattern : patterns) {
                ad.getAttributes().put(pattern.toString(), "");
            }

            Futures.addCallback(Sharing.getDiscovery().advertise(sAdvertiseContext, ad,
                    // TODO(alexfandrianto): Crypto crash if I use patterns instead of null.
                    // https://github.com/vanadium/issues/issues/1328 and
                    // https://github.com/vanadium/issues/issues/1331
                    null),
                    //patterns),
                    new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(@android.support.annotation.Nullable Void result) {
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            handleAdvertiseListError(t);
                        }
                    });
        } catch (VException e) {
            handleAdvertiseListError(e);
        }
    }

    private static void handleAdvertiseListError(Throwable t) {
        SyncbasePersistence.getAppErrorReporter().onError(R.string.err_advertise_list, t);
    }
}
