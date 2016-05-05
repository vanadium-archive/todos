// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;

import java.io.File;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import io.v.android.inspectors.RemoteInspectors;
import io.v.android.VAndroidContext;
import io.v.android.VAndroidContexts;
import io.v.android.security.BlessingsManager;
import io.v.android.error.ErrorReporter;
import io.v.android.v23.V;
import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.R;
import io.v.todos.persistence.Persistence;
import io.v.v23.OptionDefs;
import io.v.v23.Options;
import io.v.v23.VFutures;
import io.v.v23.context.VContext;
import io.v.v23.rpc.Server;
import io.v.v23.security.BlessingPattern;
import io.v.v23.security.Blessings;
import io.v.v23.security.access.AccessList;
import io.v.v23.security.access.Constants;
import io.v.v23.security.access.Permissions;
import io.v.v23.services.syncbase.CollectionRow;
import io.v.v23.services.syncbase.SyncgroupJoinFailedException;
import io.v.v23.services.syncbase.SyncgroupMemberInfo;
import io.v.v23.services.syncbase.SyncgroupSpec;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.Database;
import io.v.v23.syncbase.Syncbase;
import io.v.v23.syncbase.SyncbaseService;
import io.v.v23.syncbase.Syncgroup;
import io.v.v23.vdl.VdlStruct;
import io.v.v23.verror.CanceledException;
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
            USER_COLLECTION_SYNCGROUP_SUFFIX = "/%%sync/sg_",
            LIST_COLLECTION_SYNCGROUP_SUFFIX = "/%%sync/list_",
            DEFAULT_BLESSING_STRING = "dev.v.io:o:608941808256-43vtfndets79kf5hac8ieujto8837660" +
                    ".apps.googleusercontent.com:";
    protected static final long
            SHORT_TIMEOUT = 2500,
            RETRY_DELAY = 2000;
    public static final String
            USER_COLLECTION_NAME = "userdata",
            MOUNTPOINT = "/ns.dev.v.io:8101/tmp/todos/users/",
            CLOUD_NAME = MOUNTPOINT + "cloud",
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

    private static final Object sSyncbaseMutex = new Object();
    private static VContext sVContext;
    private static SyncbaseService sSyncbase;
    private static RemoteInspectors sRemoteInspectors;

    private static String startSyncbaseServer(VContext vContext, Context appContext,
                                              Permissions serverPermissions)
            throws SyncbaseServer.StartException {
        try {
            vContext = V.withListenSpec(vContext, V.getListenSpec(vContext).withProxy(PROXY));
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
            sRemoteInspectors = new RemoteInspectors(serverContext);
        } catch (VException e) {
            Log.w(TAG, "Unable to start remote inspection service:" + e);
        }
        // TODO(ashankar): This is not a good idea. For one, endpoints of a service may change
        // as the device changes networks. But I believe in a few weeks (end of May 2016) we'll
        // switch to a mode where there are no "local RPCs" between the syncbase client and the
        // server, so this will hopefully go away before it matters.
        return server.getStatus().getEndpoints()[0].name();
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
                final Context appContext = androidContext.getApplicationContext();
                VContext singletonContext = V.init(appContext, new Options()
                        .set(OptionDefs.LOG_VLEVEL, 0)
                        .set(OptionDefs.LOG_VMODULE, "vsync*=0"));

                try {
                    // Retrieve this context's personal permissions to set ACLs on the server.
                    Blessings personalBlessings = getPersonalBlessings(singletonContext);
                    if (personalBlessings == null) {
                        throw new IllegalStateException("Blessings must be attached to the " +
                                "Vanadium principal before Syncbase initialization.");
                    }
                    Permissions permissions = computePermissionsFromBlessings(personalBlessings);

                    sSyncbase = Syncbase.newService(startSyncbaseServer(
                            singletonContext, appContext, permissions));
                } catch (SyncbaseServer.StartException | RuntimeException e) {
                    singletonContext.cancel();
                    throw e;
                }
                sVContext = singletonContext;
            }
        }
    }

    protected static Blessings getPersonalBlessings(VContext ctx) {
        return V.getPrincipal(ctx).blessingStore().defaultBlessings();
    }

    protected static String getEmailFromBlessings(Blessings blessings) {
        String[] split = blessings.toString().split(":");
        return split[split.length - 1];
    }

    protected static String getEmailFromPattern(BlessingPattern pattern) {
        String[] split = pattern.toString().split(":");
        return split[split.length - 1];
    }

    protected static String getPersonalEmail(VContext ctx) {
        return getEmailFromBlessings(getPersonalBlessings(ctx));
    }

    protected static String blessingsStringFromEmail(String email) {
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
                final Database db = sSyncbase.getDatabase(sVContext, DATABASE, null);

                try {
                    VFutures.sync(db.create(sVContext, null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sDatabase = db;
            }
        }
    }

    private static final Object sUserCollectionMutex = new Object();
    private static volatile Collection sUserCollection;

    private static void ensureUserCollectionExists() throws VException {
        synchronized (sUserCollectionMutex) {
            if (sUserCollection == null) {
                // The reason the following doesn't work yet is that collections cross-share.
                // https://github.com/vanadium/issues/issues/1320
                //Collection userCollection = sDatabase.getCollection(sVContext,
                //        USER_COLLECTION_NAME);

                // The reason the following workaround doesn't work is the blessing is too long.
                // https://github.com/vanadium/issues/issues/1322
                // Collection userCollection = sDatabase.getCollection(
                //        new Id(getPersonalBlessings(sVContext).toString(), USER_COLLECTION_NAME));

                // TODO(alexfandrianto): Replace this with one of the above solutions when possible.
                // This solution is forced to use the email and replace invalid characters.
                String email = getPersonalEmail(sVContext).replace(".", "_").replace("@", "_AT_");
                Collection userCollection = sDatabase.getCollection(sVContext,
                        USER_COLLECTION_NAME + "_" + email);
                try {
                    VFutures.sync(userCollection.create(sVContext, null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sUserCollection = userCollection;
            }
        }
    }

    private static final Object sCloudDatabaseMutex = new Object();
    private static volatile Database sCloudDatabase;


    private static void ensureCloudDatabaseExists() {
        synchronized (sCloudDatabaseMutex) {
            if (sCloudDatabase == null) {
                SyncbaseService cloudService = Syncbase.newService(CLOUD_NAME);
                Database db = cloudService.getDatabase(sVContext, DATABASE, null);
                try {
                    VFutures.sync(db.create(sVContext.withTimeout(Duration.millis(SHORT_TIMEOUT))
                            , null));
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
    private static volatile Syncgroup sUserSyncgroup;

    private static void ensureUserSyncgroupExists() throws VException {
        synchronized (sUserSyncgroupMutex) {
            if (sUserSyncgroup == null) {
                Blessings clientBlessings = getPersonalBlessings(sVContext);
                String email = getEmailFromBlessings(clientBlessings);
                Log.d(TAG, email);

                Permissions permissions = computePermissionsFromBlessings(clientBlessings);

                String sgName = CLOUD_NAME + USER_COLLECTION_SYNCGROUP_SUFFIX + email;

                final SyncgroupMemberInfo memberInfo = getDefaultMemberInfo();
                final Syncgroup sgHandle = sDatabase.getSyncgroup(sgName);

                try {
                    Log.d(TAG, "Trying to join the syncgroup: " + sgName);
                    VFutures.sync(sgHandle.join(sVContext, memberInfo));
                    Log.d(TAG, "JOINED the syncgroup: " + sgName);
                } catch (SyncgroupJoinFailedException e) {
                    Log.w(TAG, "Failed join. Trying to create the syncgroup: " + sgName, e);
                    SyncgroupSpec spec = new SyncgroupSpec(
                            "TODOs User Data Collection", permissions,
                            ImmutableList.of(new CollectionRow(sUserCollection.id(), "")),
                            ImmutableList.of(MOUNTPOINT), false);
                    try {
                        VFutures.sync(sgHandle.create(sVContext, spec, memberInfo));
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

    protected String computeListSyncgroupName(String listId) {
        return CLOUD_NAME + LIST_COLLECTION_SYNCGROUP_SUFFIX + listId;
    }

    private static volatile boolean sInitialized;

    public static boolean isInitialized() {
        return sInitialized;
    }

    /**
     * A {@link FutureCallback} that reports persistence errors by toasting a short message to the
     * user and logging the exception trace and the call stack from where the future was invoked.
     */
    public static class TrappingCallback<T> implements FutureCallback<T> {
        private static final int FIRST_SIGNIFICANT_STACK_ELEMENT = 3;
        private final ErrorReporter mErrorReporter;
        private final StackTraceElement[] mCaller;

        public TrappingCallback(ErrorReporter errorReporter) {
            mErrorReporter = errorReporter;
            mCaller = Thread.currentThread().getStackTrace();
        }

        @Override
        public void onSuccess(@Nullable T result) {
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
            if (!(t instanceof CanceledException || t instanceof ExistException)) {
                mErrorReporter.onError(R.string.err_sync, t);

                StringBuilder traceBuilder = new StringBuilder(t.getMessage())
                        .append("\n invoked at ").append(mCaller[FIRST_SIGNIFICANT_STACK_ELEMENT]);
                for (int i = FIRST_SIGNIFICANT_STACK_ELEMENT + 1; i < mCaller.length; i++) {
                    traceBuilder.append("\n\tat ").append(mCaller[i]);
                }
                Log.e(TAG, traceBuilder.toString());
            }
        }
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
        Futures.addCallback(future, new TrappingCallback<>(getErrorReporter()));
    }

    /**
     * This constructor is blocking for simplicity.
     */
    public SyncbasePersistence(final Activity activity, Bundle savedInstanceState)
            throws VException, SyncbaseServer.StartException {
        mVAndroidContext = VAndroidContexts.withDefaults(activity, savedInstanceState);

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
        ensureSyncbaseStarted(activity);
        ensureDatabaseExists();
        ensureUserCollectionExists();
        ensureCloudDatabaseExists();
        ensureUserSyncgroupExists();
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
                return sRemoteInspectors.invite("invited-on-"+timestamp, Duration.standardDays(1));
            } catch (VException e) {
                return "Unable to setup remote inspection: " + e;
            }
        }
    }
}
