// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import io.v.todos.model.DataList;
import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.PersistenceFactory;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;

/**
 * TodoListActivity for Vanadium TODOs
 *
 * This activity shows a list of tasks.
 * - Tap on a Task in order to edit it.
 * - Swipe Left on a Task to delete it.
 * - Swipe Right in order to mark the Task as done.
 * - Select Edit from the menu to Edit the Todo List.
 * - Toggle Show Done in the menu to show/hide completed Tasks.
 *
 * @author alexfandrianto
 */
public class TodoListActivity extends Activity {
    private TodoListPersistence mPersistence;

    private ListSpec snackoo;
    private DataList<Task> snackoosList = new DataList<>();

    // This adapter handle mirrors the firebase list values and generates the corresponding todo
    // item View children for a list view.
    private TaskRecyclerAdapter adapter;

    // The menu item that toggles whether done items are shown or not.
    private MenuItem mShowDoneMenuItem;
    private boolean mShowDone; // mirrors the checked status of mShowDoneMenuItem

    @Override
    protected void onDestroy() {
        if (mPersistence != null) {
            mPersistence.close();
            mPersistence = null;
        }
        super.onDestroy();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        final String snackooKey = intent.getStringExtra(MainActivity.INTENT_SNACKOO_KEY);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setActionBar(toolbar);

        // Set up the todo list adapter
        adapter = new TaskRecyclerAdapter(snackoosList, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String key = (String) view.getTag();

                initiateTaskEdit(key);
            }
        });

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.recycler);
        recyclerView.setAdapter(adapter);

        new ItemTouchHelper(new SwipeableTouchHelperCallback() {
            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                String fbKey = (String)viewHolder.itemView.getTag();
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
                return PersistenceFactory.getTodoListPersistence(mActivity, snackooKey,
                        new TodoListListener() {
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
                                mShowDone = showDone;
                                if (mShowDoneMenuItem != null) {
                                    // Only interact with mShowDoneMenu if it has been inflated.
                                    mShowDoneMenuItem.setChecked(showDone);
                                }

                                int oldSize = adapter.getItemCount();
                                adapter.setShowDone(showDone);
                                int newSize = adapter.getItemCount();
                                if (newSize > oldSize) {
                                    adapter.notifyItemRangeInserted(oldSize, newSize - oldSize);
                                } else {
                                    adapter.notifyItemRangeRemoved(newSize, oldSize - newSize);
                                }
                                setEmptyVisiblity();
                            }

                            @Override
                            public void onItemAdd(Task item) {
                                int position = snackoosList.insertInOrder(item);
                                adapter.notifyItemInserted(position);
                                setEmptyVisiblity();
                            }

                            @Override
                            public void onItemUpdate(Task item) {
                                int start = snackoosList.findIndexByKey(item.key);
                                int end = snackoosList.updateInOrder(item);
                                adapter.notifyItemMoved(start, end);
                                adapter.notifyItemChanged(end);
                                setEmptyVisiblity();
                            }

                            @Override
                            public void onItemDelete(String key) {
                                int position = snackoosList.removeByKey(key);
                                adapter.notifyItemRemoved(position);
                                setEmptyVisiblity();
                            }
                        });
            }

            protected void onSuccess(TodoListPersistence persistence) {
                mPersistence = persistence;
            }
        }.execute(PersistenceFactory.mightGetTodoListPersistenceBlock());
    }

    // Set the visibility based on what the adapter thinks is the visible item count.
    private void setEmptyVisiblity() {
        View v = findViewById(R.id.empty);
        v.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
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

        // Since the menu item may be inflated too late, set checked to mShowDone.
        mShowDoneMenuItem.setChecked(mShowDone);

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
            case R.id.action_settings:
                return true;
            case R.id.action_edit:
                initiateTodoListEdit();
                return true;
            case R.id.action_share:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
