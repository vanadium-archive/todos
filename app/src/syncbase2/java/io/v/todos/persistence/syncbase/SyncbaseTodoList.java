// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.v.syncbase.AccessList;
import io.v.syncbase.BatchDatabase;
import io.v.syncbase.Collection;
import io.v.syncbase.Database;
import io.v.syncbase.Id;
import io.v.syncbase.User;
import io.v.syncbase.exception.SyncbaseException;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;
import io.v.todos.sharing.ShareListMenuFragment;

public class SyncbaseTodoList extends SyncbasePersistence implements TodoListPersistence {
    private Collection mCollection;
    private ShareListMenuFragment mShareListMenuFragment;

    @Override
    protected void addFeatureFragments(FragmentManager manager, Context context,
                                       FragmentTransaction transaction) {
        super.addFeatureFragments(manager, context, transaction);
        if (transaction == null) {
            mShareListMenuFragment = ShareListMenuFragment.find(manager);
        } else {
            mShareListMenuFragment = new ShareListMenuFragment();
            transaction.add(mShareListMenuFragment, ShareListMenuFragment.FRAGMENT_TAG);
        }
        mShareListMenuFragment.persistence = this;
        // TODO(alexfandrianto): I shouldn't show the sharing menu item when this person cannot
        // share the todo list with other people. (Cannot re-share in this app.)
    }

    public SyncbaseTodoList(Activity activity, Bundle savedInstanceState, String key,
                            TodoListListener listener) {
        super(activity, savedInstanceState);

        Id listId = Id.decode(key);
        mCollection = sDb.getCollection(listId);

        // Fire the listener for existing data (list, tasks, show done status).
        ListSpec currentList = sListSpecMap.get(listId);
        if (currentList != null) {
            listener.onUpdate(currentList);
        }
        Map<String, TaskSpec> currentTasks = sTasksByListMap.get(listId);
        if (currentTasks != null) {
            for (String taskKey : currentTasks.keySet()) {
                listener.onItemAdd(new Task(taskKey, currentTasks.get(taskKey)));
            }
        }
        listener.onUpdateShowDone(sShowDone);

        // Register the listener for future updates.
        setTodoListListener(listener, listId);

        // TODO(alexfandrianto): Do we want this behavior? We need getLoggedInUser() if we do.
        // if (!listId.getBlessing().equals(getPersonalBlessingsString())) {
        //     mShareListMenuFragment.hideShareMenuItem();
        // }

        // TODO(alexfandrianto): We also have to watch who the collection has been shared to!
        // mShareListMenuFragment.setSharedTo needs to happen!!!
    }

    @Override
    public void updateTodoList(ListSpec listSpec) {
        try {
            mCollection.put(TODO_LIST_KEY, listSpec);
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void deleteTodoList() {
        try {
            mCollection.delete(TODO_LIST_KEY);
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void completeTodoList() {
        // TODO(alexfandrianto): All this try catch is getting excessive.
        try {
            sDb.runInBatch(new Database.BatchOperation() {
                @Override
                public void run(BatchDatabase bDb) {
                    Collection bCollection = bDb.getCollection(mCollection.getId());
                    Map<String, TaskSpec> curTasks = sTasksByListMap.get(mCollection.getId());
                    for (Map.Entry<String, TaskSpec> entry : curTasks.entrySet()) {
                        String rowKey = entry.getKey();
                        TaskSpec curSpec = entry.getValue();
                        TaskSpec newSpec = new TaskSpec(curSpec.getText(), curSpec.getAddedAt(),
                                true);

                        // TODO(alexfandrianto): If we're in a batch, it's okay to error, isn't it?
                        try {
                            bCollection.put(rowKey, newSpec);
                        } catch (SyncbaseException e) {
                            Log.w(TAG, e);
                        }
                    }
                }
            }, new Database.BatchOptions());
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void addTask(TaskSpec task) {
        try {
            mCollection.put(UUID.randomUUID().toString(), task);
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void updateTask(Task task) {
        try {
            mCollection.put(task.key, task.toSpec());
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void deleteTask(String key) {
        try {
            mCollection.delete(key);
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void setShowDone(boolean showDone) {
        try {
            sDb.getUserdataCollection().put(SHOW_DONE_KEY, showDone);
        } catch (SyncbaseException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void close() {
        removeTodoListListener();
    }

    public void shareTodoList(Set<String> aliases) {
        List<User> users = new ArrayList<User>();
        for (String alias : aliases) {
            users.add(new User(alias));
        }
        try {
            mCollection.getSyncgroup().inviteUsers(users, AccessList.AccessLevel.READ_WRITE);
        } catch (SyncbaseException e) {
            Log.w(TAG, "Could not share to: " + users.toString(), e);
        }
    }
}
