// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.v.android.inspectors.RemoteInspectors;
import io.v.android.ManagedVAndroidContext;
import io.v.android.VAndroidContext;
import io.v.android.VAndroidContexts;
import io.v.android.error.ErrorReporter;
import io.v.android.error.ToastingErrorReporter;
import io.v.android.security.BlessingsManager;
import io.v.android.v23.V;
import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.R;
import io.v.todos.persistence.Persistence;
import io.v.todos.sharing.NeighborhoodFragment;
import io.v.todos.sharing.Sharing;
import io.v.v23.InputChannel;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.VFutures;
import io.v.v23.context.VContext;
import io.v.v23.naming.Endpoint;
import io.v.v23.rpc.ListenSpec;
import io.v.v23.rpc.Server;
import io.v.v23.security.BlessingPattern;
import io.v.v23.security.Blessings;
import io.v.v23.security.access.AccessList;
import io.v.v23.security.access.Constants;
import io.v.v23.security.access.Permissions;
import io.v.v23.services.syncbase.Id;
import io.v.v23.services.syncbase.SyncgroupJoinFailedException;
import io.v.v23.services.syncbase.SyncgroupMemberInfo;
import io.v.v23.services.syncbase.SyncgroupSpec;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.Database;
import io.v.v23.syncbase.Syncbase;
import io.v.v23.syncbase.SyncbaseService;
import io.v.v23.syncbase.Syncgroup;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.syncbase.util.Util;
import io.v.v23.vdl.VdlStruct;
import io.v.v23.verror.ExistException;
import io.v.v23.verror.VException;
import io.v.v23.vom.VomUtil;
import io.v.x.ref.lib.discovery.BadAdvertisementException;

/**
 * TODO(rosswang): Move most of this to vanadium-android.
 */
public class SyncbasePersistence implements Persistence {
    private static final String
            TAG = "SyncbasePersistence",
            FILENAME = "syncbase",
            PROXY = "proxy",
            DATABASE = "db",
            BLESSINGS_KEY = "blessings",
            USER_COLLECTION_SYNCGROUP_SUFFIX = "sg_",
            LIST_COLLECTION_SYNCGROUP_SUFFIX = "list_",
            DEFAULT_BLESSING_STRING = "dev.v.io:o:608941808256-43vtfndets79kf5hac8ieujto8837660" +
                    ".apps.googleusercontent.com:";
    protected static final String LISTS_PREFIX = "lists_";
    protected static final long
            SHORT_TIMEOUT = 2500,
            RETRY_DELAY = 300,
            MEMBER_TIMER_DELAY = 100,
            MEMBER_TIMER_PERIOD = 5000;
    public static final String
            USER_COLLECTION_NAME = "userdata",
            MOUNTPOINT = "/ns.dev.v.io:8101/tmp/todos/users/",
            CLOUD_NAME = null, // MOUNTPOINT + "cloud",
    // TODO(alexfandrianto): Restore the cloud once we can rely on it again.
    // TODO(alexfandrianto): This shouldn't be me running the cloud.
    CLOUD_BLESSING = "dev.v.io:u:alexfandrianto@google.com";

    // BlessingPattern initialization has to be deferred until after V23 init due to native binding.
    private static final Supplier<AccessList> OPEN_ACL = Suppliers.memoize(
            new Supplier<AccessList>() {
                @Override
                public AccessList get() {
                    return new AccessList(ImmutableList.of(new BlessingPattern("...")),
                            ImmutableList.<String>of());
                }
            });

    protected static final ListeningScheduledExecutorService sExecutor =
            MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(10));

    private static final Object sVContextMutex = new Object();
    private static VAndroidContext<Context> sVAndroidContext;

    private static final Object sSyncbaseMutex = new Object();
    private static SyncbaseService sSyncbase;
    private static RemoteInspectors sRemoteInspectors;

    private static void appVInit(Context appContext) {
        synchronized (sVContextMutex) {
            if (sVAndroidContext == null) {
                sVAndroidContext = new ManagedVAndroidContext<>(appContext,
                        new ToastingErrorReporter(appContext));
            }
        }
    }

    public static Context getAppContext() {
        return sVAndroidContext.getAndroidContext();
    }

    public static VContext getAppVContext() {
        return sVAndroidContext.getVContext();
    }

    public static ErrorReporter getAppErrorReporter() {
        return sVAndroidContext.getErrorReporter();
    }

    private static String startSyncbaseServer(VContext vContext, Context appContext,
                                              Permissions serverPermissions)
            throws SyncbaseServer.StartException {
        try {
            ListenSpec ls = V.getListenSpec(vContext);
            ListenSpec.Address[] addresses = ls.getAddresses();
            addresses = Arrays.copyOf(addresses, addresses.length+1);
            addresses[addresses.length-1] = new ListenSpec.Address("bt", "/0");
            ListenSpec newLs = new ListenSpec(addresses, PROXY, ls.getChooser());
            vContext = V.withListenSpec(vContext, newLs);
        } catch (VException e) {
            Log.w(TAG, "Unable to set up Vanadium proxy for Syncbase");
        }

        File storageRoot = new File(appContext.getFilesDir(), FILENAME);
        storageRoot.mkdirs();

        Log.i(TAG, "Starting Syncbase");
        SyncbaseServer.Params params = new SyncbaseServer.Params()
                .withPermissions(serverPermissions)
                .withStorageRootDir(storageRoot.getAbsolutePath());


        VContext serverContext = SyncbaseServer.withNewServer(vContext, params);

        Server server = V.getServer(serverContext);
        try {
            // TODO(ashankar): For initial debugging it is proving useful to allow remote
            // inspection to browse through syncbase data. But this should be removed at
            // some point?
            sRemoteInspectors = new RemoteInspectors(serverContext, Constants.READ);
        } catch (VException e) {
            Log.w(TAG, "Unable to start remote inspection service:" + e);
        }
        // TODO(ashankar): This is not a good idea. For one, endpoints of a service may change
        // as the device changes networks. But I believe in a few weeks (mid June 2016) we'll
        // switch to a mode where there are no "local RPCs" between the syncbase client and the
        // server, so this will hopefully go away before it matters.
        // TODO(suharshs): Although in a few weeks (mid June 2016) the new mode mentioned above
        // will solve this issue, there is currently still an bug where initialization can hang due
        // to the wrong endpoint getting returned here. In particular, it is important that the
        // endpoint returned is locally accessible for the "local RPC" to succeed.
        // So for now, we make sure to return an locally accessible endpoint.
        Endpoint[] endpoints = server.getStatus().getEndpoints();
        for (Endpoint ep : endpoints) {
            try {
                String[] hostPort = ep.address().address().split(":");
                if (hostPort.length != 2) {
                    continue;
                }
                InetAddress addr = InetAddress.getByName(hostPort[0]);
                if (addr.isLoopbackAddress()) {
                    return ep.name();
                }
            } catch (UnknownHostException e) {
                // Try the next address.
            }
        }
        String errString = "";
        for (Endpoint ep : endpoints) {
            errString += " " + ep.name();
        }
        Log.e(TAG, "No locally accessible addresses in" + errString);
        return endpoints[0].name();
    }

    /**
     * Ensures that Syncbase is running. This should not be called until after the Vanadium
     * principal has assumed blessings. The Syncbase server will run until the process is killed.
     *
     * @throws IllegalStateException                                            if blessings were
     *                                                                          not attached to
     *                                                                          the principal
     *                                                                          beforehand
     * @throws io.v.impl.google.services.syncbase.SyncbaseServer.StartException if there was an
     *                                                                          error starting
     *                                                                          the syncbase service
     */
    private static void ensureSyncbaseStarted(Context androidContext)
            throws SyncbaseServer.StartException, VException {
        synchronized (sSyncbaseMutex) {
            if (sSyncbase == null) {
                VContext serverRun = getAppVContext().withCancel();
                try {
                    // Retrieve this context's personal permissions to set ACLs on the server.
                    Blessings personalBlessings = getPersonalBlessings();
                    if (personalBlessings == null) {
                        throw new IllegalStateException("Blessings must be attached to the " +
                                "Vanadium principal before Syncbase initialization.");
                    }
                    Permissions permissions = computePermissionsFromBlessings(personalBlessings);

                    sSyncbase = Syncbase.newService(startSyncbaseServer(
                            serverRun, getAppContext(), permissions));
                } catch (SyncbaseServer.StartException | RuntimeException e) {
                    serverRun.cancel();
                    throw e;
                }
            }
        }
    }

    // TODO(rosswang): Factor into v23
    public static Blessings getPersonalBlessings() {
        return V.getPrincipal(getAppVContext()).blessingStore().defaultBlessings();
    }

    public static String getPersonalBlessingsString() {
        return getPersonalBlessings().toString();
    }

    public static String getEmailFromBlessings(Blessings blessings) {
        // TODO(alexfandrianto): This should be in v23, but it should also verify that the app
        // component is the right one in the blessing.
        return getEmailFromBlessingsString(blessings.toString());
    }

    public static String getEmailFromPattern(BlessingPattern pattern) {
        return getEmailFromBlessingsString(pattern.toString());
    }

    public static String getEmailFromBlessingsString(String blessingsStr) {
        String[] split = blessingsStr.split(":");
        return split[split.length - 1];
    }

    public static String getPersonalEmail() {
        return getEmailFromBlessings(getPersonalBlessings());
    }

    public static String blessingsStringFromEmail(String email) {
        // TODO(alexfandrianto): We may need a more sophisticated method for producing this
        // blessings string. Currently, the app's id is fixed to the anonymous Android app.
        return DEFAULT_BLESSING_STRING + email;
    }

    protected static Permissions computePermissionsFromBlessings(Blessings blessings) {
        AccessList clientAcl = new AccessList(ImmutableList.of(
                new BlessingPattern(blessings.toString()), new BlessingPattern(CLOUD_BLESSING)),
                ImmutableList.<String>of());

        return new Permissions(ImmutableMap.of(
                Constants.RESOLVE.getValue(), OPEN_ACL.get(),
                Constants.READ.getValue(), clientAcl,
                Constants.WRITE.getValue(), clientAcl,
                Constants.ADMIN.getValue(), clientAcl));
    }

    private static final Object sDatabaseMutex = new Object();
    private static Database sDatabase;

    private static void ensureDatabaseExists() throws VException {
        synchronized (sDatabaseMutex) {
            if (sDatabase == null) {
                final Database db = sSyncbase.getDatabase(getAppVContext(), DATABASE, null);

                try {
                    VFutures.sync(db.create(getAppVContext(), null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sDatabase = db;
            }
        }
    }

    private static final Object sUserCollectionMutex = new Object();
    private static Collection sUserCollection;

    private static void ensureUserCollectionExists() throws VException {
        synchronized (sUserCollectionMutex) {
            if (sUserCollection == null) {
                Collection userCollection = sDatabase.getCollection(
                        new Id(getPersonalBlessingsString(), USER_COLLECTION_NAME));
                try {
                    VFutures.sync(userCollection.create(getAppVContext(), null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sUserCollection = userCollection;
            }
        }
    }

    private static final Object sCloudDatabaseMutex = new Object();
    private static Database sCloudDatabase;

    private static void ensureCloudDatabaseExists() {
        synchronized (sCloudDatabaseMutex) {
            if (sCloudDatabase == null) {
                SyncbaseService cloudService = Syncbase.newService(CLOUD_NAME);
                Database db = cloudService.getDatabase(getAppVContext(), DATABASE, null);
                try {
                    VFutures.sync(db.create(getAppVContext()
                            .withTimeout(Duration.millis(SHORT_TIMEOUT)), null));
                } catch (ExistException e) {
                    // This is acceptable. No need to do it again.
                } catch (Exception e) {
                    Log.w(TAG, "Could not ensure cloud database exists: " + e.getMessage());
                }
                sCloudDatabase = db;
            }
        }
    }

    private static final Object sUserSyncgroupMutex = new Object();
    private static Syncgroup sUserSyncgroup;

    private static void ensureUserSyncgroupExists() throws VException {
        synchronized (sUserSyncgroupMutex) {
            if (sUserSyncgroup == null) {
                Blessings clientBlessings = getPersonalBlessings();
                String email = getEmailFromBlessings(clientBlessings);
                Log.d(TAG, email);

                Permissions permissions = computePermissionsFromBlessings(clientBlessings);

                String sgName = USER_COLLECTION_SYNCGROUP_SUFFIX + Math.abs(email.hashCode());

                final SyncgroupMemberInfo memberInfo = getDefaultMemberInfo();
                final Syncgroup sgHandle = sDatabase.getSyncgroup(new Id(clientBlessings.toString
                        (), sgName));

                try {
                    Log.d(TAG, "Trying to join the syncgroup: " + sgName);
                    VFutures.sync(sgHandle.join(getAppVContext(), CLOUD_NAME,
                            Arrays.asList(CLOUD_BLESSING), memberInfo));
                    Log.d(TAG, "JOINED the syncgroup: " + sgName);
                } catch (SyncgroupJoinFailedException e) {
                    Log.w(TAG, "Failed join. Trying to create the syncgroup: " + sgName, e);
                    SyncgroupSpec spec = new SyncgroupSpec(
                            "TODOs User Data Collection", CLOUD_NAME, permissions,
                            ImmutableList.of(sUserCollection.id()),
                            ImmutableList.of(MOUNTPOINT), false);
                    try {
                        VFutures.sync(sgHandle.create(getAppVContext(), spec, memberInfo));
                    } catch (BadAdvertisementException e2) {
                        Log.d(TAG, "Bad advertisement exception. Can we fix this?");
                    }
                    Log.d(TAG, "CREATED the syncgroup: " + sgName);
                } catch (Exception e) {
                    Log.d(TAG, "Failed to join or create the syncgroup: " + sgName);
                    if (!(e instanceof BadAdvertisementException)) { // joined, I guess
                        throw e;
                    }
                }
                sUserSyncgroup = sgHandle;
            }
        }
    }

    protected static SyncgroupMemberInfo getDefaultMemberInfo() {
        SyncgroupMemberInfo memberInfo = new SyncgroupMemberInfo();
        memberInfo.setSyncPriority((byte) 3);
        return memberInfo;

    }

    protected static String computeListSyncgroupName(String listId) {
        return LIST_COLLECTION_SYNCGROUP_SUFFIX + listId;
    }

    private static String BLESSING_NAME_SEPARATOR = "___";
    public static String convertIdToString(Id id) {
        // Put the name first since it has a useful prefix for watch to switch on.
        return id.getName() + BLESSING_NAME_SEPARATOR + id.getBlessing();
    }
    public static Id convertStringToId(String idString) {
        String[] parts = idString.split(BLESSING_NAME_SEPARATOR);
        return new Id(parts[1], parts[0]);
    }

    private static volatile boolean sInitialized;

    public static boolean isInitialized() {
        return sInitialized;
    }

    /**
     * Extracts the value from a watch change or scan stream.
     * TODO(rosswang): This method is a temporary hack, awaiting resolution of the following issues:
     * <ul>
     * <li><a href="https://github.com/vanadium/issues/issues/1305">#1305</a>
     * <li><a href="https://github.com/vanadium/issues/issues/1310">#1310</a>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static <T> T castFromSyncbase(Object watchValue, Class<T> type) {
        if (type.isInstance(watchValue)) {
            return (T) watchValue;
        }

        try {
            return (T) VomUtil.decode(VomUtil.encode((VdlStruct) watchValue), type);
        } catch (VException e) {
            Log.e(TAG, Throwables.getStackTraceAsString(e));
            throw new ClassCastException("Could not cast " + watchValue + " to " + type);
        }
    }

    protected class SyncTrappingCallback<T> extends TrappingCallback<T> {
        public SyncTrappingCallback() {
            super(R.string.err_sync, TAG, getErrorReporter());
        }
    }

    private final VAndroidContext<Activity> mVAndroidContext;

    public VContext getVContext() {
        return mVAndroidContext.getVContext();
    }

    public ErrorReporter getErrorReporter() {
        return mVAndroidContext.getErrorReporter();
    }

    @Override
    public void close() {
        mVAndroidContext.close();
    }

    protected Database getDatabase() {
        return sDatabase;
    }

    protected Collection getUserCollection() {
        return sUserCollection;
    }

    /**
     * @see TrappingCallback
     */
    protected void trap(ListenableFuture<?> future) {
        Futures.addCallback(future, new SyncTrappingCallback<>());
    }

    /**
     * Hook to insert or rebind fragments.
     *
     * @param manager
     * @param transaction the fragment transaction to use to add fragments, or null if fragments are
     *                    being restored by the system.
     */
    @CallSuper
    protected void addFeatureFragments(FragmentManager manager,
                                       @Nullable FragmentTransaction transaction) {
        if (transaction != null) {
            transaction.add(new NeighborhoodFragment(), NeighborhoodFragment.FRAGMENT_TAG);
        }
    }

    /**
     * This constructor is blocking for simplicity.
     */
    public SyncbasePersistence(final Activity activity, Bundle savedInstanceState)
            throws VException, SyncbaseServer.StartException {
        mVAndroidContext = VAndroidContexts.withDefaults(activity, savedInstanceState);

        FragmentManager mgr = activity.getFragmentManager();
        if (savedInstanceState == null) {
            FragmentTransaction t = mgr.beginTransaction();
            addFeatureFragments(mgr, t);
            t.commit();
        } else {
            addFeatureFragments(mgr, null);
        }

        // We might not actually have to seek blessings each time, but getBlessings does not
        // block if we already have blessings and this has better-behaved lifecycle
        // implications than trying to seek blessings in the static code.
        final SettableFuture<ListenableFuture<Blessings>> blessings = SettableFuture.create();
        if (activity.getMainLooper().getThread() == Thread.currentThread()) {
            blessings.set(BlessingsManager.getBlessings(getVContext(), activity,
                    BLESSINGS_KEY, true));
        } else {
            new Handler(activity.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    blessings.set(BlessingsManager.getBlessings(getVContext(),
                            activity, BLESSINGS_KEY, true));
                }
            });
        }

        VFutures.sync(Futures.dereference(blessings));
        appVInit(activity.getApplicationContext());
        final SyncbasePersistence self = this;
        /*final Future<?> ensureCloudDatabaseExists = sExecutor.submit(new Runnable() {
            @Override
            public void run() {
                ensureCloudDatabaseExists();
            }
        });*/
        ensureSyncbaseStarted(activity);
        ensureDatabaseExists();
        ensureUserCollectionExists();
        // TODO(alexfandrianto): If the cloud is dependent on me, then we must do this too.
        // VFutures.sync(ensureCloudDatabaseExists); // must finish before syncgroup setup
        ensureUserSyncgroupExists();
        Sharing.initDiscovery(); // requires that db and collection exist
        sInitialized = true;
    }

    @Override
    public String debugDetails() {
        synchronized (sSyncbaseMutex) {
            if (sRemoteInspectors == null) {
                return "Syncbase has not been initialized";
            }
            final String timestamp = DateTimeFormat.forPattern("yyyy-MM-dd").print(new DateTime());
            try {
                return sRemoteInspectors.invite("invited-on-" + timestamp, Duration.standardDays
                        (1));
            } catch (VException e) {
                return "Unable to setup remote inspection: " + e;
            }
        }
    }

    public static void acceptSharedTodoList(final Id listId) {
        sExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws VException {
                Boolean exists = VFutures.sync(sUserCollection.getRow(convertIdToString(listId)).
                        exists(getAppVContext()));
                if (!exists) {
                    VFutures.sync(rememberTodoList(listId));
                }
                return null;
            }
        });
    }

    protected static ListenableFuture<Void> rememberTodoList(Id listId) {
        return sUserCollection.put(getAppVContext(), convertIdToString(listId), "");
    }

    public static ListenableFuture<Void> watchUserCollection(InputChannelCallback<WatchChange>
                                                                     callback) {
        InputChannel<WatchChange> watch = sDatabase.watch(getAppVContext(),
                ImmutableList.of(Util.rowPrefixPattern(sUserCollection.id(), LISTS_PREFIX)));
        return InputChannels.withCallback(watch, callback);
    }

    public static Timer watchSharedTo(final Id listId, final Function<List<BlessingPattern>,
            Void> callback) {
        final Syncgroup sgHandle = sDatabase.getSyncgroup(new Id(listId.getBlessing(),
                computeListSyncgroupName(listId.getName())));

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            private SyncgroupSpec lastSpec;

            @Override
            public void run() {
                Map<String, SyncgroupSpec> specMap;
                try {
                    // Ok to block; we don't want to try parallel polls.
                    specMap = VFutures.sync(sgHandle.getSpec(getAppVContext()));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get syncgroup spec for list: " + listId, e);
                    return;
                }

                String version = Iterables.getOnlyElement(specMap.keySet());
                SyncgroupSpec spec = specMap.get(version);

                if (spec.equals(lastSpec)) {
                    return; // no changes, so no event should fire.
                }
                Log.d(TAG, "Spec changed for list: " + listId);
                lastSpec = spec;

                Permissions perms = spec.getPerms();
                AccessList acl = perms.get(Constants.READ.getValue());
                List<BlessingPattern> patterns = acl.getIn();

                callback.apply(patterns);
            }
        }, MEMBER_TIMER_DELAY, MEMBER_TIMER_PERIOD);
        return timer;
    }
}
