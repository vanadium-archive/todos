// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toolbar;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

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
    private Firebase myFirebaseRef;

    private final static String SNACKOO_LISTS = "snackoo lists (Task)";
    private String snackooKey;
    private TodoList snackoo;
    private DataList<Task> snackoosList = new DataList<Task>();
    private boolean showDone = false; // TODO(alexfandrianto): Load from shared preferences...

    // This adapter handle mirrors the firebase list values and generates the corresponding todo
    // item View children for a list view.
    private TaskRecyclerAdapter adapter;
    private ValueEventListener snackooEventListener;
    private ChildEventListener snackoosEventListener;

    @Override
    protected void onDestroy() {
        myFirebaseRef.removeEventListener(snackooEventListener);
        myFirebaseRef.removeEventListener(snackoosEventListener);
        super.onDestroy();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        snackooKey = intent.getStringExtra(MainActivity.INTENT_SNACKOO_KEY);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setActionBar(toolbar);

        // Set up the todo list adapter
        final Activity self = this;
        adapter = new TaskRecyclerAdapter(snackoosList, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String key = (String) view.getTag();

                initiateTaskEdit(key);
            }
        });

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.recycler);
        recyclerView.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            private Paint paint = new Paint();
            private Bitmap deleteIcon = BitmapFactory.decodeResource(
                    getResources(), android.R.drawable.ic_input_delete);
            private Bitmap doneIcon = BitmapFactory.decodeResource(
                    getResources(), android.R.drawable.checkbox_on_background);

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                // TODO(alexfandrianto): Refactor further. Is there another way to do this?
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    // Get RecyclerView item from the ViewHolder
                    View itemView = viewHolder.itemView;

                    if (dX > 0) {
                        /* Set your color for positive displacement */
                        paint.setColor(0xFF00FF00);

                        // Draw Rect with varying right side, equal to displacement dX
                        c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX,
                                (float) itemView.getBottom(), paint);

                        c.drawBitmap(doneIcon,
                                (float) itemView.getLeft() + 32,
                                (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - doneIcon.getHeight())/2,
                                paint);
                    } else {
                        /* Set your color for negative displacement */
                        paint.setColor(0xFFFF0000);

                        // Draw Rect with varying left side, equal to the item's right side plus negative displacement dX
                        c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(),
                                (float) itemView.getRight(), (float) itemView.getBottom(), paint);


                        //Set the image icon for Left swipe
                        c.drawBitmap(deleteIcon,
                                (float) itemView.getRight() - 32 - deleteIcon.getWidth(),
                                (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - deleteIcon.getHeight())/2,
                                paint);
                    }

                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                }
            }

            @Override
            public boolean onMove(final RecyclerView recyclerView,
                                  final RecyclerView.ViewHolder viewHolder,
                                  final RecyclerView.ViewHolder target) {


                /*editListStructure(l -> l.add(target.getAdapterPosition(),
                        l.remove(viewHolder.getAdapterPosition())));*/

                // TODO(alexfandrianto): Actually, I really doubt that we want to do this. It's super complex..
                Log.d(SNACKOO_LISTS, "Moving is hard.");
                return false;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                if (direction == ItemTouchHelper.RIGHT) {
                    Log.d(SNACKOO_LISTS, "Gonna mark all tasks as done");
                    markAsDone((String)viewHolder.itemView.getTag());
                } else if (direction == ItemTouchHelper.LEFT) {
                    Log.d(SNACKOO_LISTS, "Gonna delete this todo list");
                    deleteTodoItem((String)viewHolder.itemView.getTag());
                }
            }
        }).attachToRecyclerView(recyclerView);

        // Set up Firebase with the context and tell it to persist data locally even if we're offline.
        Firebase.setAndroidContext(this);
        //Firebase.getDefaultConfig().setPersistenceEnabled(true);

        // Prepare our Firebase Reference and the primary listener (SNACKOOS).
        myFirebaseRef = new Firebase(MainActivity.FIREBASE_EXAMPLE_URL);
        setUpSnackoo();
        setUpSnackoos();
    }

    private Firebase firebaseListReference() {
        return myFirebaseRef.child(MainActivity.SNACKOOS).child(snackooKey);
    }

    private Firebase firebaseTasksReference() {
        return myFirebaseRef.child(SNACKOO_LISTS).child(snackooKey);
    }

    private void setUpSnackoo() {
        snackooEventListener = firebaseListReference().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                snackoo = dataSnapshot.getValue(TodoList.class);
                if (snackoo == null) {
                    // The list has been deleted. Get the heck out of here!
                    finish();
                    return;
                }
                getActionBar().setTitle(snackoo.getName());
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    // Set the visibility based on what the adapter thinks is the visible item count.
    private void setEmptyVisiblity() {
        View v = findViewById(R.id.empty);
        v.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // Any time a child of SNACKOOS is added/changed/removed, we mirror the changes locally.
    private void setUpSnackoos() {
        snackoosEventListener = firebaseTasksReference().addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
                String fbKey = dataSnapshot.getKey();
                Task task = dataSnapshot.getValue(Task.class);
                task.setKey(fbKey);
                snackoosList.insertInOrder(task);
                adapter.notifyDataSetChanged();

                setEmptyVisiblity();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
                String fbKey = dataSnapshot.getKey();
                Task task = dataSnapshot.getValue(Task.class);
                task.setKey(fbKey);
                snackoosList.updateInOrder(task);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String fbKey = dataSnapshot.getKey();
                snackoosList.removeByKey(fbKey);
                adapter.notifyDataSetChanged();

                setEmptyVisiblity();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String prevKey) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    public void addTodoItem(String todo) {
        // TODO(alexfandrianto): Turns out these are all batch changes that change the parents updatedAt
        firebaseTasksReference().push().setValue(new Task(todo));
    }

    public void updateTodoItem(String fbKey, String todo) {
        // TODO(alexfandrianto): Turns out these are all batch changes that change the parents updatedAt
        Task task = snackoosList.findByKey(fbKey).copy();
        task.setText(todo);
        firebaseTasksReference().child(fbKey).setValue(task);
    }
    public void markAsDone(String fbKey) {
        // TODO(alexfandrianto): Turns out these are all batch changes that change the parents updatedAt
        Task task = snackoosList.findByKey(fbKey).copy();
        task.setDone(!task.getDone());
        firebaseTasksReference().child(fbKey).setValue(task);
    }

    public void deleteTodoItem(String fbKey) {
        // TODO(alexfandrianto): Turns out these are all batch changes that change the parents updatedAt
        firebaseTasksReference().child(fbKey).removeValue();
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
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }


    public void updateTodoList(String todo) {
        firebaseListReference().setValue(new TodoList(todo));
    }

    public void deleteTodoList() {
        firebaseListReference().removeValue();
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
