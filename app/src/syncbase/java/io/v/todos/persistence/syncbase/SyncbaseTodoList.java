// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;
import io.v.v23.InputChannel;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.VFutures;
import io.v.v23.context.VContext;
import io.v.v23.security.BlessingPattern;
import io.v.v23.security.access.AccessList;
import io.v.v23.security.access.Constants;
import io.v.v23.security.access.Permissions;
import io.v.v23.services.syncbase.SyncgroupSpec;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.Syncgroup;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.verror.NoExistException;
import io.v.v23.verror.VException;

public class SyncbaseTodoList extends SyncbasePersistence implements TodoListPersistence {
    public static final String
            TAG = "SyncbaseTodoList",
            LIST_METADATA_ROW_NAME = "list",
            TASKS_PREFIX = "tasks_";

    private static final String
            SHOW_DONE_ROW_NAME = "ShowDone";

    private static final long
            MEMBER_TIMER_DELAY = 100,
            MEMBER_TIMER_PERIOD = 5000;

    private final Collection mList;
    private final TodoListListener mListener;
    private final IdGenerator mIdGenerator = new IdGenerator(IdAlphabets.ROW_NAME, true);
    private final Set<String> mTaskIds = new HashSet<>();
    private final Timer mMemberTimer;

    /**
     * This assumes that the collection for this list already exists.
     */
    public SyncbaseTodoList(Activity activity, Bundle savedInstanceState, String listId,
                            TodoListListener listener)
            throws VException, SyncbaseServer.StartException {
        super(activity, savedInstanceState);
        mListener = listener;

        mList = getDatabase().getCollection(getVContext(), listId);
        InputChannel<WatchChange> listWatch = getDatabase().watch(getVContext(), mList.id(), "");
        ListenableFuture<Void> listWatchFuture = InputChannels.withCallback(listWatch,
                new InputChannelCallback<WatchChange>() {
                    @Override
                    public ListenableFuture<Void> onNext(WatchChange change) {
                        processWatchChange(change);
                        return null;
                    }
                });
        Futures.addCallback(listWatchFuture, new TrappingCallback<Void>(getErrorReporter()) {
            @Override
            public void onFailure(@NonNull Throwable t) {
                if (t instanceof NoExistException) {
                    // The collection has been deleted.
                    mListener.onDelete();
                } else {
                    super.onFailure(t);
                }
            }
        });

        final Syncgroup sgHandle = getListSyncgroup();
        mMemberTimer = new Timer();
        mMemberTimer.scheduleAtFixedRate(new TimerTask() {
            private SyncgroupSpec lastSpec;

            @Override
            public void run() {
                Map<String, SyncgroupSpec> specMap;
                try {
                    // Ok to block; we don't want to try parallel polls.
                    specMap = VFutures.sync(sgHandle.getSpec(getVContext()));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get syncgroup spec for list: " + mList.id().getName(), e);
                    return;
                }

                String version = Iterables.getOnlyElement(specMap.keySet());
                SyncgroupSpec spec = specMap.get(version);

                if (spec.equals(lastSpec)) {
                    return; // no changes, so no event should fire.
                }
                lastSpec = spec;

                // Get the list of patterns that can read from the spec.
                Permissions perms = spec.getPerms();
                AccessList acl = perms.get(Constants.READ.getValue());
                List<BlessingPattern> patterns = acl.getIn();

                // Analyze these patterns to construct the emails, and fire the listener!
                List<String> emails = parseEmailsFromPatterns(patterns);
                mListener.onShareChanged(emails);
            }
        }, MEMBER_TIMER_DELAY, MEMBER_TIMER_PERIOD);

        // Watch the "showDone" boolean in the userdata collection and forward changes to the
        // listener.
        InputChannel<WatchChange> showDoneWatch = getDatabase()
                .watch(getVContext(), getUserCollection().id(), SHOW_DONE_ROW_NAME);
        trap(InputChannels.withCallback(showDoneWatch, new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange result) {
                mListener.onUpdateShowDone((boolean) result.getValue());
                return null;
            }
        }));
    }

    protected List<String> parseEmailsFromPatterns(List<BlessingPattern> patterns) {
        List<String> emails = new ArrayList<>();

        for (BlessingPattern pattern : patterns) {
            if (pattern.isMatchedBy(CLOUD_BLESSING)) {
                // Skip. It's the cloud, and that doesn't count.
                continue;
            }
            if (pattern.toString().endsWith(getPersonalEmail(getVContext()))) {
                // Skip. It's you, and that doesn't count.
                continue;
            }
            emails.add(getEmailFromPattern(pattern));
        }
        return emails;
    }

    @Override
    public void close() {
        mMemberTimer.cancel();
        super.close();
    }

    private void processWatchChange(WatchChange change) {
        String rowName = change.getRowName();

        if (rowName.equals(SyncbaseTodoList.LIST_METADATA_ROW_NAME)) {
            ListSpec listSpec = SyncbasePersistence.castFromSyncbase(change.getValue(),
                    ListSpec.class);
            mListener.onUpdate(listSpec);
        } else if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
            mTaskIds.remove(rowName);
            mListener.onItemDelete(rowName);
        } else {
            mIdGenerator.registerId(change.getRowName().substring(TASKS_PREFIX.length()));

            TaskSpec taskSpec = SyncbasePersistence.castFromSyncbase(change.getValue(),
                    TaskSpec.class);
            Task task = new Task(rowName, taskSpec);

            if (mTaskIds.add(rowName)) {
                mListener.onItemAdd(task);
            } else {
                mListener.onItemUpdate(task);
            }
        }
    }

    @Override
    public void updateTodoList(ListSpec listSpec) {
        trap(mList.put(getVContext(), LIST_METADATA_ROW_NAME, listSpec, ListSpec.class));
    }

    @Override
    public void deleteTodoList() {
        trap(getUserCollection().delete(getVContext(), mList.id().getName()));
        trap(mList.destroy(getVContext()));
    }

    private Syncgroup getListSyncgroup() {
        return getDatabase().getSyncgroup(computeListSyncgroupName(mList.id().getName()));
    }

    @Override
    public void shareTodoList(final Iterable<String> emails) {
        // Get the syncgroup
        final Syncgroup sgHandle = getListSyncgroup();

        // Get the Syncgroup Spec and add read access. Then get the collection permissions and add
        // both read and write access. Along the way, trigger the listener's onShareChanged.

        trap(sExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Map<String, SyncgroupSpec> specMap = VFutures.sync(sgHandle.getSpec(getVContext()));

                String version = Iterables.getOnlyElement(specMap.keySet());
                SyncgroupSpec spec = specMap.get(version);

                // Modify the syncgroup spec to update the permissions.
                Permissions perms = spec.getPerms();
                addPermissions(perms, emails, Constants.READ.getValue());
                VFutures.sync(sgHandle.setSpec(getVContext(), spec, version));

                // TODO(alexfandrianto): This should be the right place to send the invite
                // explicitly to the selected emails.

                // Analyze these patterns to construct the emails, and fire the listener!
                List<String> specEmails = parseEmailsFromPatterns(
                        perms.get(Constants.READ.getValue()).getIn());
                mListener.onShareChanged(specEmails);

                // Add read and write access to the collection permissions.
                perms = VFutures.sync(mList.getPermissions(getVContext()));

                addPermissions(perms, emails, Constants.READ.getValue());
                addPermissions(perms, emails, Constants.WRITE.getValue());
                VFutures.sync(mList.setPermissions(getVContext(), perms));
                return null;
            }
        }));
    }

    // TODO(alexfandrianto): We should consider moving this helper into the main Java repo.
    // https://github.com/vanadium/issues/issues/1321
    private static void addPermissions(Permissions perms, Iterable<String> emails, String tag) {
        AccessList acl = perms.get(tag);
        List<BlessingPattern> patterns = acl.getIn();
        for (String email : emails) {
            patterns.add(new BlessingPattern(blessingsStringFromEmail(email)));
        }
        perms.put(tag, acl);
    }

    public static ListenableFuture<Void> updateListTimestamp(final VContext vContext,
                                                             final Collection list) {
        ListenableFuture<Object> get = list.get(vContext, LIST_METADATA_ROW_NAME, ListSpec.class);
        return Futures.transformAsync(get, new AsyncFunction<Object, Void>() {
            @Override
            public ListenableFuture<Void> apply(Object oldValue) throws Exception {
                ListSpec listSpec = (ListSpec) oldValue;
                listSpec.setUpdatedAt(System.currentTimeMillis());
                return list.put(vContext, LIST_METADATA_ROW_NAME, listSpec, ListSpec.class);
            }
        });
    }

    private void updateListTimestamp() {
        trap(updateListTimestamp(getVContext(), mList));
    }

    @Override
    public void addTask(TaskSpec task) {
        trap(mList.put(getVContext(), TASKS_PREFIX + mIdGenerator.generateTailId(), task,
                TaskSpec.class));
        updateListTimestamp();
    }

    @Override
    public void updateTask(Task task) {
        trap(mList.put(getVContext(), task.key, task.toSpec(), TaskSpec.class));
        updateListTimestamp();
    }

    @Override
    public void deleteTask(String key) {
        trap(mList.delete(getVContext(), key));
        updateListTimestamp();
    }

    @Override
    public void setShowDone(boolean showDone) {
        trap(getUserCollection().put(getVContext(), SHOW_DONE_ROW_NAME, showDone, Boolean.TYPE));
    }
}
