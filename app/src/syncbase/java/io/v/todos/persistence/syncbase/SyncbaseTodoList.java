// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.support.annotation.NonNull;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashSet;
import java.util.Set;

import io.v.impl.google.services.syncbase.SyncbaseServer;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;
import io.v.v23.InputChannel;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.context.VContext;
import io.v.v23.syncbase.ChangeType;
import io.v.v23.syncbase.Collection;
import io.v.v23.syncbase.WatchChange;
import io.v.v23.verror.NoExistException;
import io.v.v23.verror.VException;

public class SyncbaseTodoList extends SyncbasePersistence implements TodoListPersistence {
    public static final String
            LIST_ROW_NAME = "list",
            TASKS_PREFIX = "tasks_";

    private static final String
            SHOW_DONE_ROW_NAME = "ShowDone";

    private final Collection mList;
    private final TodoListListener mListener;
    private final Set<String> mTaskIds = new HashSet<>();

    /**
     * This assumes that the collection for this list already exists.
     */
    public SyncbaseTodoList(Activity activity, String listId, TodoListListener listener)
            throws VException, SyncbaseServer.StartException {
        super(activity);
        mListener = listener;

        mList = getDatabase().getCollection(mVContext, listId);
        InputChannel<WatchChange> listWatch = getDatabase().watch(mVContext, mList.id(), "");
        ListenableFuture<Void> listWatchFuture = InputChannels.withCallback(listWatch,
                new InputChannelCallback<WatchChange>() {
                    @Override
                    public ListenableFuture<Void> onNext(WatchChange change) {
                        processWatchChange(change);
                        return null;
                    }
                });
        Futures.addCallback(listWatchFuture, new TrappingCallback<Void>(activity) {
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

        // Watch the "showDone" boolean in the userdata collection and forward changes to the
        // listener.
        InputChannel<WatchChange> showDoneWatch = getDatabase()
                .watch(mVContext, getUserCollection().id(), SHOW_DONE_ROW_NAME);
        trap(InputChannels.withCallback(showDoneWatch, new InputChannelCallback<WatchChange>() {
            @Override
            public ListenableFuture<Void> onNext(WatchChange result) {
                mListener.onUpdateShowDone((boolean)result.getValue());
                return null;
            }
        }));
    }

    private void processWatchChange(WatchChange change) {
        String rowName = change.getRowName();

        if (rowName.equals(SyncbaseTodoList.LIST_ROW_NAME)) {
            ListSpec listSpec = SyncbasePersistence.castWatchValue(change.getValue(),
                    ListSpec.class);
            mListener.onUpdate(listSpec);
        } else if (change.getChangeType() == ChangeType.DELETE_CHANGE) {
            mTaskIds.remove(rowName);
            mListener.onItemDelete(rowName);
        } else {
            TaskSpec taskSpec = SyncbasePersistence.castWatchValue(change.getValue(),
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
        trap(mList.put(mVContext, LIST_ROW_NAME, listSpec, ListSpec.class));
    }

    @Override
    public void deleteTodoList() {
        trap(getUserCollection().delete(mVContext, mList.id().getName()));
        trap(mList.destroy(mVContext));
    }

    public static ListenableFuture<Void> updateListTimestamp(final VContext vContext,
                                                             final Collection list) {
        ListenableFuture<Object> get = list.get(vContext, LIST_ROW_NAME, ListSpec.class);
        return Futures.transform(get, new AsyncFunction<Object, Void>() {
            @Override
            public ListenableFuture<Void> apply(Object oldValue) throws Exception {
                ListSpec listSpec = (ListSpec) oldValue;
                listSpec.setUpdatedAt(System.currentTimeMillis());
                return list.put(vContext, LIST_ROW_NAME, listSpec, ListSpec.class);
            }
        });
    }

    private void updateListTimestamp() {
        trap(updateListTimestamp(mVContext, mList));
    }

    @Override
    public void addTask(TaskSpec task) {
        trap(mList.put(mVContext, TASKS_PREFIX + randomName(), task, TaskSpec.class));
        updateListTimestamp();
    }

    @Override
    public void updateTask(Task task) {
        trap(mList.put(mVContext, task.key, task.toSpec(), TaskSpec.class));
        updateListTimestamp();
    }

    @Override
    public void deleteTask(String key) {
        trap(mList.delete(mVContext, key));
        updateListTimestamp();
    }

    @Override
    public void setShowDone(boolean showDone) {
        trap(getUserCollection().put(mVContext, SHOW_DONE_ROW_NAME, showDone, Boolean.TYPE));
    }
}
