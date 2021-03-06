// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

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
    private DataList<Task> snackoosList = new DataList<>();

    // The menu item that toggles whether done items are shown or not.
    private MenuItem mShowDoneMenuItem;

    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);

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
        }, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String key = (String) view.getTag();

                mPersistence.updateTask(snackoosList.findByKey(key).withToggleDone());
            }
        });

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setHasFixedSize(true);

        new ItemTouchHelper(new SwipeableTouchHelperCallback(0, ItemTouchHelper.LEFT |
                ItemTouchHelper.RIGHT) {
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
                finishWithAnimation();
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

        switch (item.getItemId()) {
            case R.id.show_done:
                mPersistence.setShowDone(!item.isChecked());
                return true;
            case R.id.action_edit:
                initiateTodoListEdit();
                return true;
            case R.id.action_all_done:
                mPersistence.completeTodoList();
                return true;
            case R.id.action_debug:
                sharePersistenceDebugDetails();
                return true;
            case android.R.id.home:
                finishWithAnimation();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finishWithAnimation();
    }

    private void finishWithAnimation() {
        this.finish();
        overridePendingTransition(R.anim.left_slide_in, R.anim.right_slide_out);
    }
}
