// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.v.todos.model.DataList;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.PersistenceFactory;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;

/**
 * TodoListActivity for Vanadium TODOs
 * <p/>
 * This activity shows a list of tasks.
 * - Tap on a Task in order to edit it.
 * - Swipe Left on a Task to delete it.
 * - Swipe Right in order to mark the Task as done.
 * - Select Edit from the menu to Edit the Todo List.
 * - Toggle Show Done in the menu to show/hide completed Tasks.
 *
 * @author alexfandrianto
 */
public class TodoListActivity extends TodosAppActivity<TodoListPersistence, TaskRecyclerAdapter> {
    private ListSpec snackoo;
    private List<String> mSharedTo = new ArrayList<>();
    private DataList<Task> snackoosList = new DataList<>();

    // The menu item that toggles whether done items are shown or not.
    private MenuItem mShowDoneMenuItem;

    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEmptyView.setText(R.string.no_tasks);

        Intent intent = getIntent();
        final String snackooKey = intent.getStringExtra(MainActivity.INTENT_SNACKOO_KEY);

        // Set up the todo list adapter
        mAdapter = new TaskRecyclerAdapter(snackoosList, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String key = (String) view.getTag();

                initiateTaskEdit(key);
            }
        });

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setHasFixedSize(true);

        new ItemTouchHelper(new SwipeableTouchHelperCallback() {
            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                String fbKey = (String) viewHolder.itemView.getTag();
                if (direction == ItemTouchHelper.RIGHT) {
                    mPersistence.updateTask(snackoosList.findByKey(fbKey).withToggleDone());
                } else if (direction == ItemTouchHelper.LEFT) {
                    mPersistence.deleteTask(fbKey);
                }
            }
        }).attachToRecyclerView(recyclerView);

        new PersistenceInitializer<TodoListPersistence>(this) {
            @Override
            protected TodoListPersistence initPersistence() throws Exception {
                return PersistenceFactory.getTodoListPersistence(mActivity, savedInstanceState,
                        snackooKey, createTodoListListener());
            }

            protected void onSuccess(TodoListPersistence persistence) {
                mPersistence = persistence;
                setEmptyVisiblity();
            }
        }.execute(PersistenceFactory.mightGetTodoListPersistenceBlock());
    }

    // Creates a listener for this activity. Visible to tests to allow them to invoke the listener
    // methods directly.
    @VisibleForTesting
    TodoListListener createTodoListListener() {
        return new TodoListListener() {
            @Override
            public void onUpdate(ListSpec value) {
                snackoo = value;
                getActionBar().setTitle(snackoo.getName());
            }

            @Override
            public void onDelete() {
                finish();
            }

            @Override
            public void onUpdateShowDone(boolean showDone) {
                if (mShowDoneMenuItem != null) {
                    // Only interact with mShowDoneMenu if it has been inflated.
                    mShowDoneMenuItem.setChecked(showDone);
                }

                int oldSize = mAdapter.getItemCount();
                mAdapter.setShowDone(showDone);
                int newSize = mAdapter.getItemCount();
                if (newSize > oldSize) {
                    mAdapter.notifyItemRangeInserted(oldSize, newSize - oldSize);
                } else {
                    mAdapter.notifyItemRangeRemoved(newSize, oldSize - newSize);
                }
                setEmptyVisiblity();
            }

            @Override
            public void onShareChanged(List<String> sharedTo) {
                mSharedTo = sharedTo;
            }

            @Override
            public void onItemAdd(Task item) {
                int position = snackoosList.insertInOrder(item);
                mAdapter.notifyItemInserted(position);
                setEmptyVisiblity();
            }

            @Override
            public void onItemUpdate(Task item) {
                int start = snackoosList.findIndexByKey(item.key);
                int end = snackoosList.updateInOrder(item);
                mAdapter.notifyItemMoved(start, end);
                mAdapter.notifyItemChanged(end);
                setEmptyVisiblity();
            }

            @Override
            public void onItemDelete(String key) {
                int position = snackoosList.removeByKey(key);
                mAdapter.notifyItemRemoved(position);
                setEmptyVisiblity();
            }
        };
    }

    public void initiateItemAdd(View view) {
        UIUtil.showAddDialog(this, "New Task", new UIUtil.DialogResponseListener() {
            @Override
            public void handleResponse(String response) {
                mPersistence.addTask(new TaskSpec(response));
            }
        });
    }

    private void initiateTaskEdit(final String fbKey) {
        UIUtil.showEditDialog(this, "Editing Task", snackoosList.findByKey(fbKey).text,
                new UIUtil.DialogResponseListener() {
                    @Override
                    public void handleResponse(String response) {
                        mPersistence.updateTask(snackoosList.findByKey(fbKey).withText(response));
                    }

                    @Override
                    public void handleDelete() {
                        mPersistence.deleteTask(fbKey);
                    }
                });
    }

    private void initiateTodoListEdit() {
        UIUtil.showEditDialog(this, "Editing Todo List", snackoo.getName(),
                new UIUtil.DialogResponseListener() {
                    @Override
                    public void handleResponse(String response) {
                        mPersistence.updateTodoList(new ListSpec(response));
                    }

                    @Override
                    public void handleDelete() {
                        mPersistence.deleteTodoList();
                    }
                });
    }

    // The following methods are boilerplate for handling the Menu in the top right corner.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_task, menu);

        // Also, obtain the reference to the show done menu item.
        mShowDoneMenuItem = menu.findItem(R.id.show_done);

        // Since the menu item may be inflated too late, set checked to the adapter's value.
        mShowDoneMenuItem.setChecked(mAdapter.getShowDone());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.show_done:
                mPersistence.setShowDone(!item.isChecked());
                return true;
            case R.id.action_edit:
                initiateTodoListEdit();
                return true;
            case R.id.action_debug:
                sharePersistenceDebugDetails();
                return true;
            case R.id.action_share:
                // TODO(alexfandrianto): We should figure out who is near us.
                List<String> fakeNearby = new ArrayList<>();

                // TODO(alexfandrianto): mSharedTo will not be live-updated, so the dialog can show
                // stale shares. Perhaps this dialog should return a notifier object.
                UIUtil.showShareDialog(this, mSharedTo, fakeNearby,
                        new UIUtil.ShareDialogResponseListener() {
                            @Override
                            public void handleShareChanges(Set<String> emailsAdded, Set<String>
                                    emailsRemoved) {
                                Log.d("SHARE COMPLETE!", emailsAdded.toString() + emailsRemoved
                                        .toString());
                                if (emailsAdded.size() > 0) {
                                    mPersistence.shareTodoList(emailsAdded);
                                    // TODO(alexfandrianto): We may need to advertise this somehow.
                                }
                                // TODO(alexfandrianto): We can't actually handle removing
                                // members yet, so it may be better to hide the ability to remove in
                                // the share dialog.
                            }
                        });
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
