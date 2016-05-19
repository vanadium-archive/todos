// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.firebase;

import android.content.Context;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;

public class FirebaseMain extends FirebasePersistence implements MainPersistence {
    public static final String TODO_LISTS = "snackoos (TodoList)";

    private final Firebase mTodoLists;
    private final ChildEventListener mTodoListsListener;

    private final ListEventListener<ListMetadata> mListener;

    private final Map<String, ChildEventListener> mTodoListTaskListeners;
    private final Map<String, TodoListTasksListener> mTodoListTrackers;

    public FirebaseMain(Context context, final ListEventListener<ListMetadata> listener) {
        super(context);

        mTodoLists = getFirebase().child(TODO_LISTS);

        // This handler will forward events to the passed in listener after ensuring that all the
        // data in the ListMetadata is set and can automatically update.
        mTodoListsListener = mTodoLists.addChildEventListener(
                new ChildEventListenerAdapter() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
                        mListener.onItemAdd(startWatchTodoListTasks(
                                dataSnapshot.getKey(), dataSnapshot.getValue(ListSpec.class)));
                    }

                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
                        mListener.onItemUpdate(updateListSpec(
                                dataSnapshot.getKey(), dataSnapshot.getValue(ListSpec.class)));
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                        stopWatchTodoListTasks(dataSnapshot.getKey());
                        mListener.onItemDelete(dataSnapshot.getKey());
                    }
                });

        mListener = listener;
        mTodoListTaskListeners = new HashMap<>();
        mTodoListTrackers = new HashMap<>();
    }

    @Override
    public void addTodoList(ListSpec listSpec) {
        mTodoLists.push().setValue(listSpec);
    }

    @Override
    public void deleteTodoList(String key) {
        mTodoLists.child(key).removeValue();

        // After deleting the list itself, delete all the orphaned tasks!
        Firebase tasksRef = getFirebase().child(FirebaseTodoList.TASKS).child(key);
        tasksRef.removeValue();
    }

    private ListMetadata updateListSpec(String key, ListSpec updatedSpec) {
        TodoListTasksListener tracker = mTodoListTrackers.get(key);
        tracker.listSpec = updatedSpec;
        return tracker.getListMetadata();
    }

    private ListMetadata startWatchTodoListTasks(String key, final ListSpec listSpec) {
        Firebase taskRef = getFirebase().child(FirebaseTodoList.TASKS).child(key);
        TodoListTasksListener tasksListener = new TodoListTasksListener(key, listSpec);
        ChildEventListener l = taskRef.addChildEventListener(
                new TaskChildEventListener(tasksListener));
        mTodoListTrackers.put(key, tasksListener);
        mTodoListTaskListeners.put(key, l);
        return tasksListener.getListMetadata();
    }

    private void stopWatchTodoListTasks(String key) {
        mTodoListTrackers.remove(key).disable(); // Disable; we don't want this listener anymore.
        ChildEventListener l = mTodoListTaskListeners.remove(key);
        getFirebase().removeEventListener(l);
    }

    @Override
    public void close() {
        getFirebase().removeEventListener(mTodoListsListener);
        for (ChildEventListener listener : mTodoListTaskListeners.values()) {
            getFirebase().removeEventListener(listener);
        }
    }

    private class TodoListTasksListener implements ListEventListener<Task> {
        final String listKey;
        ListSpec listSpec;
        final Set<String> completedTaskKeys;
        int numTasks;
        boolean disabled;

        TodoListTasksListener(String listKey, ListSpec listSpec) {
            this.listKey = listKey;
            this.listSpec = listSpec;

            completedTaskKeys = new HashSet<>();
        }

        // Prevent this listener from propagating any more updates.
        // Note: It looks like Firebase will continue firing listeners if they have more data, so
        // call this if you absolutely don't need any more events to fire.
        void disable() {
            disabled = true;
        }

        ListMetadata getListMetadata() {
            return new ListMetadata(listKey, listSpec, completedTaskKeys.size(), numTasks);
        }

        @Override
        public void onItemAdd(Task item) {
            if (disabled) {
                return;
            }
            numTasks++;
            if (item.done) {
                completedTaskKeys.add(item.key);
            }

            mListener.onItemUpdate(getListMetadata());
        }

        @Override
        public void onItemUpdate(Task item) {
            if (disabled) {
                return;
            }

            // Short-circuiting performs the appropriate Set update (add if done, remove if not).
            boolean changedDone =
                    item.done && completedTaskKeys.add(item.key) ||
                    !item.done && completedTaskKeys.remove(item.key);

            if (changedDone) {
                mListener.onItemUpdate(getListMetadata());
            }
        }

        @Override
        public void onItemDelete(String key) {
            if (disabled) {
                return;
            }
            numTasks--;
            completedTaskKeys.remove(key);

            mListener.onItemUpdate(getListMetadata());
        }
    }
}
