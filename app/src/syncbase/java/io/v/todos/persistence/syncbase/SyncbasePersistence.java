// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import io.v.android.libs.security.BlessingsManager;
import io.v.android.v23.V;
import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.R;
import io.v.todos.persistence.Persistence;
import io.v.v23.VFutures;
import io.v.v23.context.VContext;
import io.v.v23.rpc.Server;
import io.v.v23.security.BlessingPattern;
import io.v.v23.security.Blessings;
import io.v.v23.security.access.AccessList;
import io.v.v23.security.access.Constants;
import io.v.v23.security.access.Permissions;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.Database;
import io.v.v23.syncbase.Syncbase;
import io.v.v23.syncbase.SyncbaseService;
import io.v.v23.vdl.VdlStruct;
import io.v.v23.verror.CanceledException;
import io.v.v23.verror.ExistException;
import io.v.v23.verror.VException;
import io.v.v23.vom.VomUtil;

/**
 * TODO(rosswang): Move most of this to vanadium-android.
 */
public class SyncbasePersistence implements Persistence {
    private static final String
            TAG = "SyncbasePersistence",
            FILENAME = "syncbase",
            PROXY = "proxy",
            DATABASE = "db",
            BLESSINGS_KEY = "blessings";
    public static final String
            USER_COLLECTION_NAME = "userdata";
    // BlessingPattern initialization has to be deferred until after V23 init due to native binding.
    private static final Supplier<AccessList> OPEN_ACL = Suppliers.memoize(
            new Supplier<AccessList>() {
                @Override
                public AccessList get() {
                    return new AccessList(ImmutableList.of(new BlessingPattern("...")),
                            ImmutableList.<String>of());
                }
            });

    protected static final ListeningExecutorService sExecutor =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    private static final Object sSyncbaseMutex = new Object();
    private static VContext sVContext;
    private static SyncbaseService sSyncbase;

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
        VContext serverContext = SyncbaseServer.withNewServer(vContext,
                new SyncbaseServer.Params()
                        .withPermissions(serverPermissions)
                        .withStorageRootDir(storageRoot.getAbsolutePath()));

        Server server = V.getServer(serverContext);
        return "/" + server.getStatus().getEndpoints()[0];
    }

    /**
     * Ensures that Syncbase is running. This should not be called until after the Vanadium
     * principal has assumed blessings. The Syncbase server will run until the process is killed.
     *
     * @throws IllegalStateException if blessings were not attached to the principal beforehand
     * @throws io.v.impl.google.services.syncbase.SyncbaseServer.StartException if there was an
     * error starting the syncbase service
     */
    private static void ensureSyncbaseStarted(Context androidContext)
            throws SyncbaseServer.StartException {
        synchronized (sSyncbaseMutex) {
            if (sSyncbase == null) {
                final Context appContext = androidContext.getApplicationContext();
                VContext singletonContext = V.init(appContext);
                try {
                    Blessings clientBlessings = V.getPrincipal(singletonContext)
                            .blessingStore().defaultBlessings();
                    if (clientBlessings == null) {
                        throw new IllegalStateException("Blessings must be attached to the " +
                                "Vanadium principal before Syncbase initialization.");
                    }

                    AccessList clientAcl = new AccessList(ImmutableList.of(
                            new BlessingPattern(clientBlessings.toString())),
                            ImmutableList.<String>of());

                    Permissions permissions = new Permissions(ImmutableMap.of(
                            Constants.RESOLVE.getValue(), OPEN_ACL.get(),
                            Constants.READ.getValue(), clientAcl,
                            Constants.WRITE.getValue(), clientAcl,
                            Constants.ADMIN.getValue(), clientAcl));

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
                Collection userCollection = sDatabase.getCollection(sVContext,
                        USER_COLLECTION_NAME);
                try {
                    VFutures.sync(userCollection.create(sVContext, null));
                } catch (ExistException e) {
                    // This is fine.
                }
                sUserCollection = userCollection;
            }
        }
    }

    public static boolean isInitialized() {
        return sUserCollection != null;
    }

    protected static String randomName() {
        return UUID.randomUUID().toString().replace('-', '_');
    }

    /**
     * A {@link FutureCallback} that reports persistence errors by toasting a short message to the
     * user and logging the exception trace and the call stack from where the future was invoked.
     */
    public static class TrappingCallback<T> implements FutureCallback<T> {
        private static final int FIRST_SIGNIFICANT_STACK_ELEMENT = 3;
        private final Context mAndroidContext;
        private final StackTraceElement[] mCaller;

        public TrappingCallback(Context androidContext) {
            mAndroidContext = androidContext;
            mCaller = Thread.currentThread().getStackTrace();
        }

        @Override
        public void onSuccess(@Nullable T result) {
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
            if (!(t instanceof CanceledException)) {
                Toast.makeText(mAndroidContext, R.string.err_sync, Toast.LENGTH_LONG).show();
                StringBuilder traceBuilder = new StringBuilder(Throwables.getStackTraceAsString(t))
                        .append("\n invoked at ").append(mCaller[FIRST_SIGNIFICANT_STACK_ELEMENT]);
                for (int i = FIRST_SIGNIFICANT_STACK_ELEMENT + 1; i < mCaller.length; i++) {
                    traceBuilder.append("\n\tat ").append(mCaller[i]);
                }
                Log.e(TAG, traceBuilder.toString());
            }
        }
    }

    /**
     * Extracts the value from a watch change.
     * TODO(rosswang): This method is a tempory hack, awaiting resolution of the following issues:
     *
     * <ul>
     *  <li><a href="https://github.com/vanadium/issues/issues/1305">#1305</a>
     *  <li><a href="https://github.com/vanadium/issues/issues/1310">#1310</a>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static <T> T castWatchValue(Object watchValue, Class<T> type) {
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

    protected final Activity mActivity;
    protected final VContext mVContext;

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
        Futures.addCallback(future, new TrappingCallback<>(mActivity));
    }

    /**
     * This constructor is blocking for simplicity.
     */
    public SyncbasePersistence(final Activity activity)
            throws VException, SyncbaseServer.StartException {
        mActivity = activity;
        mVContext = V.init(activity);

        // We might not actually have to seek blessings each time, but getBlessings does not
        // block if we already have blessings and this has better-behaved lifecycle
        // implications than trying to seek blessings in the static code.
        final SettableFuture<ListenableFuture<Blessings>> blessings = SettableFuture.create();
        if (activity.getMainLooper().getThread() == Thread.currentThread()) {
            blessings.set(BlessingsManager.getBlessings(mVContext, activity, BLESSINGS_KEY, true));
        } else {
            new Handler(activity.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    blessings.set(BlessingsManager.getBlessings(
                            mVContext, activity, BLESSINGS_KEY, true));
                }
            });
        }
        VFutures.sync(Futures.dereference(blessings));
        ensureSyncbaseStarted(activity);
        ensureDatabaseExists();
        ensureUserCollectionExists();
    }

    @Override
    public void close() {
        mVContext.cancel();
    }
}
