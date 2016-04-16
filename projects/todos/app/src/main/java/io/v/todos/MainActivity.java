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

import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;
import io.v.todos.persistence.PersistenceFactory;

/**
 * MainActivity for Vanadium TODOs
 *
 * This activity shows a list of todo lists.
 * - Tap on a Todo List to launch its corresponding TodoListActivity.
 * - Swipe Left on a Todo List to delete it.
 * - Swipe Right in order to mark all of its Tasks as done.
 *
 * @author alexfandrianto
 */
public class MainActivity extends Activity {
    private MainPersistence mPersistence;

    // Snackoos are the code name for the list of todos.
    // These todos are backed up at the SNACKOOS child of the Firebase URL.
    // We use the snackoosList to track a custom sorted list of the stored values.
    static final String INTENT_SNACKOO_KEY = "snackoo key";
    private DataList<TodoList> snackoosList = new DataList<TodoList>();

    // This adapter handle mirrors the firebase list values and generates the corresponding todo
    // item View children for a list view.
    private TodoListRecyclerAdapter adapter;

    @Override
    protected void onDestroy() {
        mPersistence.close();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        getActionBar().setTitle(R.string.app_name);

        // Set up the todo list adapter
        adapter = new TodoListRecyclerAdapter(snackoosList, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String fbKey = (String)view.getTag();

                Intent intent = new Intent(MainActivity.this, TodoListActivity.class);
                intent.putExtra(INTENT_SNACKOO_KEY, fbKey);
                startActivity(intent);
            }
        });

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.recycler);
        recyclerView.setAdapter(adapter);

        // TODO(alexfandrianto): Very much copy-pasted between MainActivity and TodoListActivity.
        new ItemTouchHelper(new SwipeableTouchHelperCallback() {
            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                if (direction == ItemTouchHelper.RIGHT) {
                    // TODO(alexfandrianto): This doesn't do anything yet. Should mark all child
                    // Tasks as done.
                    adapter.notifyDataSetChanged();
                } else if (direction == ItemTouchHelper.LEFT) {
                    mPersistence.deleteTodoList((String)viewHolder.itemView.getTag());
                }
            }
        }).attachToRecyclerView(recyclerView);

        mPersistence = PersistenceFactory.getMainPersistence(this, new ListEventListener<TodoList>() {
            @Override
            public void onInsert(TodoList item) {
                snackoosList.insertInOrder(item);
                adapter.notifyDataSetChanged();

                // TODO(alexfandrianto): In order to capture the computed values for this TodoList,
                // we have to watch the Task's data.

                setEmptyVisiblity();
            }

            @Override
            public void onUpdate(TodoList item) {
                snackoosList.updateInOrder(item);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onDelete(String key) {
                snackoosList.removeByKey(key);
                adapter.notifyDataSetChanged();

                // TODO(alexfandrianto): Stop watching the Task data for this TodoList.

                setEmptyVisiblity();
            }
        });
    }

    // Set the visibility based on what the adapter thinks is the visible item count.
    private void setEmptyVisiblity() {
        View v = findViewById(R.id.empty);
        v.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    public void addCallback(View view) {
        final EditText todoItem = new EditText(this);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New Todo")
                .setView(todoItem)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mPersistence.addTodoList(new TodoList(todoItem.getText().toString()));
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    // The following methods are boilerplate for handling the Menu in the top right corner.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
