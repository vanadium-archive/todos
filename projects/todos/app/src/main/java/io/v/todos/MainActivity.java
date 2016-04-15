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

import io.v.todos.persistence.FirebasePersistence;

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
    static final String FIREBASE_EXAMPLE_URL = "https://vivid-heat-7354.firebaseio.com/";
    private Firebase myFirebaseRef;

    // Snackoos are the code name for the list of todos.
    // These todos are backed up at the SNACKOOS child of the Firebase URL.
    // We use the snackoosList to track a custom sorted list of the stored values.
    static final String INTENT_SNACKOO_KEY = "snackoo key";
    static final String INTENT_SNACKOO_VALUE = "snackoo value";
    static final String SNACKOOS = "snackoos (TodoList)";
    private DataList<TodoList> snackoosList = new DataList<TodoList>();

    // This adapter handle mirrors the firebase list values and generates the corresponding todo
    // item View children for a list view.
    private TodoListRecyclerAdapter adapter;

    private ChildEventListener snackoosEventListener;

    @Override
    protected void onDestroy() {
        myFirebaseRef.removeEventListener(snackoosEventListener);
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
        final Activity self = this;
        adapter = new TodoListRecyclerAdapter(snackoosList, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String fbKey = (String)view.getTag();

                Intent intent = new Intent(self, TodoListActivity.class);
                intent.putExtra(INTENT_SNACKOO_KEY, fbKey);
                startActivity(intent);
            }
        });

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.recycler);
        recyclerView.setAdapter(adapter);

        // TODO(alexfandrianto): Very much copy-pasted between MainActivity and TodoListActivity.
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
                Log.d(SNACKOOS, "Moving is hard.");
                return false;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                if (direction == ItemTouchHelper.RIGHT) {
                    Log.d(SNACKOOS, "Gonna mark all tasks as done");

                    // TODO(alexfandrianto): This doesn't do anything yet. Should mark all child Tasks as done.
                    adapter.notifyDataSetChanged();
                } else if (direction == ItemTouchHelper.LEFT) {
                    Log.d(SNACKOOS, "Gonna delete this todo list");
                    deleteTodoItem((String)viewHolder.itemView.getTag());
                }
            }
        }).attachToRecyclerView(recyclerView);

        // Prepare our Firebase Reference and the primary listener (SNACKOOS).
        FirebasePersistence.getDatabase(this);
        myFirebaseRef = new Firebase(FIREBASE_EXAMPLE_URL);
        setUpSnackoos();
    }

    // Set the visibility based on what the adapter thinks is the visible item count.
    private void setEmptyVisiblity() {
        View v = findViewById(R.id.empty);
        v.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // Any time a child of SNACKOOS is added/changed/removed, we mirror the changes locally.
    private void setUpSnackoos() {
        snackoosEventListener = firebaseListReference().addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevKey) {
                String fbKey = dataSnapshot.getKey();
                TodoList todoList = dataSnapshot.getValue(TodoList.class);
                todoList.setKey(fbKey);

                // Insert in order.
                snackoosList.insertInOrder(todoList);

                adapter.notifyDataSetChanged();

                // TODO(alexfandrianto): In order to capture the computed values for this TodoList,
                // we have to watch the Task's data.

                setEmptyVisiblity();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String prevKey) {
                String fbKey = dataSnapshot.getKey();
                TodoList todoList = dataSnapshot.getValue(TodoList.class);
                todoList.setKey(fbKey);

                snackoosList.updateInOrder(todoList);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String fbKey = dataSnapshot.getKey();
                snackoosList.removeByKey(fbKey);
                adapter.notifyDataSetChanged();

                // TODO(alexfandrianto): Stop watching the Task data for this TodoList.

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

    private Firebase firebaseListReference() {
        return myFirebaseRef.child(SNACKOOS);
    }
    public void addTodoItem(String todo) {
        firebaseListReference().push().setValue(new TodoList(todo));
    }

    public void deleteTodoItem(String fbKey) {
        firebaseListReference().child(fbKey).removeValue();
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
