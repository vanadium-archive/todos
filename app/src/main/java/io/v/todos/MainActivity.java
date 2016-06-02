// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import io.v.todos.model.DataList;
import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
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
public class MainActivity extends TodosAppActivity<MainPersistence, TodoListRecyclerAdapter> {
    // Snackoos are the code name for the list of todos.
    // These todos are backed up at the SNACKOOS child of the Firebase URL.
    // We use mMainList to track a custom sorted list of the stored values.
    static final String INTENT_SNACKOO_KEY = "snackoo key";
    private DataList<ListMetadata> mMainList = new DataList<>();

    private RecyclerView mRecyclerView;

    private static final int BLE_LOCATION_PERMISSIONS_REQUEST_CODE = 1;

    private PersistenceInitializer<MainPersistence> mPersistenceInitializer;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEmptyView.setText(R.string.no_lists);

        // Set up the todo list adapter
        mAdapter = new TodoListRecyclerAdapter(mMainList, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String fbKey = (String)view.getTag();
                enterList(fbKey);
            }
        });

        mRecyclerView = (RecyclerView)findViewById(R.id.recycler);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);

        new ItemTouchHelper(new SwipeableTouchHelperCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                String todoListKey = (String)viewHolder.itemView.getTag();
                if (direction == ItemTouchHelper.LEFT) {
                    mPersistence.deleteTodoList(todoListKey);
                }
            }
        }).attachToRecyclerView(mRecyclerView);

        mPersistenceInitializer = new PersistenceInitializer<MainPersistence>(this) {
            @Override
            protected MainPersistence initPersistence() throws Exception {
                return PersistenceFactory.getMainPersistence(mActivity, savedInstanceState,
                        createMainListener());
            }

            @Override
            protected void onSuccess(MainPersistence persistence) {
                mPersistence = persistence;
                setEmptyVisiblity();
            }
        };

        // mPersistenceInitializer.execute is invoked once we request location permissions from the
        // app user.
        requestLocationPermissions();
    }

    // Creates a listener for this activity. Visible to tests to allow them to invoke the listener
    // methods directly.
    @VisibleForTesting
    ListEventListener<ListMetadata> createMainListener() {
        return new ListEventListener<ListMetadata>() {
            @Override
            public void onItemAdd(ListMetadata item) {
                int position = mMainList.insertInOrder(item);

                mAdapter.notifyItemInserted(position);
                setEmptyVisiblity();
            }

            @Override
            public void onItemUpdate(final ListMetadata item) {
                int start = mMainList.findIndexByKey(item.key);
                int end = mMainList.updateInOrder(item);

                if (start != end) {
                    mAdapter.notifyItemMoved(start, end);
                }

                // The change animation involves a cross-fade that, if interrupted
                // while another for the same item is already in progress, interacts
                // badly with ItemTouchHelper's swipe animator. The effect would be
                // a flicker of the intermediate ListMetadata view, then it fading
                // out to the latest view but X-translated off the screen due to the
                // swipe animator.
                //
                // We could queue up the next change after the current one, but it's
                // probably better just to rebind.
                TodoListViewHolder vh = (TodoListViewHolder) mRecyclerView
                        .findViewHolderForAdapterPosition(end);
                if (vh != null && vh.itemView.getAlpha() < 1) {
                    mAdapter.bindViewHolder(vh, end);
                } else {
                    mAdapter.notifyItemChanged(end);
                }
            }

            @Override
            public void onItemDelete(String key) {
                int position = mMainList.removeByKey(key);

                mAdapter.notifyItemRemoved(position);
                setEmptyVisiblity();
            }
        };
    }

    public void initiateItemAdd(View view) {
        UIUtil.showAddDialog(this, "New Todo List", new UIUtil.DialogResponseListener() {
            @Override
            public void handleResponse(String response) {
                String newKey = mPersistence.addTodoList(new ListSpec(response));
                enterList(newKey);
            }
        });
    }

    private void enterList(final String listKey) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                Intent intent = new Intent(MainActivity.this, TodoListActivity.class);
                intent.putExtra(INTENT_SNACKOO_KEY, listKey);
                startActivity(intent);
                overridePendingTransition(R.anim.right_slide_in, R.anim.left_slide_out);
            }
        }, 100); // Use a short delay to allow the keyboard to disappear.
    }

    // The following methods are boilerplate for handling the Menu in the top right corner.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_debug) {
            sharePersistenceDebugDetails();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // TODO(suharshs): Move this into the BLE Driver code so that all app developers don't have to
    // request this.
    public void requestLocationPermissions() {
        // Request location permissions at run-time to allow BLE scanning.
        // As per: https://developer.android.com/training/permissions/requesting.html.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    BLE_LOCATION_PERMISSIONS_REQUEST_CODE);
        } else {
            mPersistenceInitializer.execute(PersistenceFactory.mightGetMainPersistenceBlock());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case BLE_LOCATION_PERMISSIONS_REQUEST_CODE: {
                mPersistenceInitializer.execute(PersistenceFactory.mightGetMainPersistenceBlock());
                return;
            }
        }
    }
}
