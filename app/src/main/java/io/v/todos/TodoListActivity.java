// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toolbar;

import io.v.todos.model.DataList;
import io.v.todos.model.Task;
import io.v.todos.model.TodoList;
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

    private TodoList snackoo;
    private DataList<Task> snackoosList = new DataList<Task>();
    private boolean showDone = false; // TODO(alexfandrianto): Load from shared preferences...

    // This adapter handle mirrors the firebase list values and generates the corresponding todo
    // item View children for a list view.
    private TaskRecyclerAdapter adapter;

    @Override
    protected void onDestroy() {
        mPersistence.close();
        super.onDestroy();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        String snackooKey = intent.getStringExtra(MainActivity.INTENT_SNACKOO_KEY);

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
                if (direction == ItemTouchHelper.RIGHT) {
                    markAsDone((String)viewHolder.itemView.getTag());
                } else if (direction == ItemTouchHelper.LEFT) {
                    deleteTodoItem((String)viewHolder.itemView.getTag());
                }
            }
        }).attachToRecyclerView(recyclerView);

        mPersistence = PersistenceFactory.getTodoListPersistence(this, snackooKey,
                new TodoListListener() {
            @Override
            public void onUpdate(TodoList value) {
                snackoo = value;
                getActionBar().setTitle(snackoo.getName());
            }

            @Override
            public void onDelete() {
                finish();
            }

            @Override
            public void onItemAdd(Task item) {
                int position = snackoosList.insertInOrder(item);
                adapter.notifyItemInserted(position);
                setEmptyVisiblity();
            }

            @Override
            public void onItemUpdate(Task item) {
                int start = snackoosList.findIndexByKey(item.getKey());
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

    // Set the visibility based on what the adapter thinks is the visible item count.
    private void setEmptyVisiblity() {
        View v = findViewById(R.id.empty);
        v.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    public void addTodoItem(String todo) {
        mPersistence.addTask(new Task(todo));
    }

    public void updateTodoItem(String fbKey, String todo) {
        Task task = snackoosList.findByKey(fbKey).copy();
        task.setText(todo);
        mPersistence.updateTask(task);
    }
    public void markAsDone(String fbKey) {
        Task task = snackoosList.findByKey(fbKey).copy();
        task.setDone(!task.getDone());
        mPersistence.updateTask(task);
    }

    public void deleteTodoItem(String fbKey) {
        mPersistence.deleteTask(fbKey);
    }

    public void addCallback(View view) {
        final EditText todoItem = new EditText(this);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New Todo")
                .setView(todoItem)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        addTodoItem(todoItem.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void initiateTaskEdit(final String fbKey) {
        final EditText todoItem = new EditText(this);
        todoItem.setText(snackoosList.findByKey(fbKey).getText());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editing Task")
                .setView(todoItem)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        updateTodoItem(fbKey, todoItem.getText().toString());
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        deleteTodoItem(fbKey);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void initiateTodoListEdit() {
        final EditText todoItem = new EditText(this);
        todoItem.setText(snackoo.getName());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editing Todo List")
                .setView(todoItem)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        updateTodoList(todoItem.getText().toString());
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        deleteTodoList();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }


    public void updateTodoList(String todo) {
        mPersistence.updateTodoList(new TodoList(todo));
    }

    public void deleteTodoList() {
        mPersistence.deleteTodoList();
    }


    // The following methods are boilerplate for handling the Menu in the top right corner.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_task, menu);
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
                if(item.isChecked()){
                    item.setChecked(false);
                    adapter.setShowDone(false);
                }else{
                    item.setChecked(true);
                    adapter.setShowDone(true);
                }
                adapter.notifyDataSetChanged();

                // TODO(alexfandrianto): You may wish to save this data into SharedPreferences.
                // You may also wish to save this to a different part of the space which is synced
                // across your devices.

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
