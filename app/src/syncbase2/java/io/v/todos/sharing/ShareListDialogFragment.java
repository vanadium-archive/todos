// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.sharing;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.v.syncbase.Syncbase;
import io.v.syncbase.User;
import io.v.todos.R;

/**
 * The share dialog contains two recycler views. The top one shows the existing items, and the
 * bottom shows ones that are nearby but not shared to yet. There's also a freeform text box to
 * allow entry of any value. Confirming this dialog sends the set of added and removed emails. Tap
 * to add/remove.
 */
public class ShareListDialogFragment extends DialogFragment
        implements ContactAdapter.ContactTouchListener {
    public static final String FRAGMENT_TAG = ShareListDialogFragment.class.getSimpleName();

    public static ShareListDialogFragment find(FragmentManager fragmentManager) {
        return (ShareListDialogFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
    }

    private RecyclerView mContacts;
    private Set<String> mRemoved, mAdded, mRecent;

    private static final String
            REMOVED_KEY = "removedShares",
            ADDED_KEY = "addedShares",
            RECENT_KEY = "recentShares";

    private ContactAdapter mAdapter;

    private ShareListMenuFragment getParent() {
        return ((ShareListMenuFragment) getParentFragment());
    }

    @Override
    public void onDestroyView() {
        rmAdapter(mAdapter);
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putStringArrayList(REMOVED_KEY, new ArrayList<>(mRemoved));
        outState.putStringArrayList(ADDED_KEY, new ArrayList<>(mAdded));
        outState.putStringArrayList(RECENT_KEY, new ArrayList<>(mRecent));
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getActivity().getLayoutInflater().inflate(R.layout.sharing, null);

        if (savedInstanceState == null) {
            mRemoved = new HashSet<>();
            mAdded = new HashSet<>();
            mRecent = new HashSet<>();
        } else {
            mRemoved = new HashSet<>(savedInstanceState.getStringArrayList(REMOVED_KEY));
            mAdded = new HashSet<>(savedInstanceState.getStringArrayList(ADDED_KEY));
            mRecent = new HashSet<>(savedInstanceState.getStringArrayList(RECENT_KEY));
        }

        mAdapter = new ContactAdapter(getParent().getSharedTo(), mAdded, mRemoved, mRecent);
        mAdapter.setContactTouchListener(this);
        mContacts = (RecyclerView) view.findViewById(R.id.recycler);
        mContacts.setAdapter(mAdapter);
        addAdapter(mAdapter);

        final EditText editText = (EditText) view.findViewById(R.id.custom_email);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String email = editText.getText().toString();
                    mAdapter.onCustomShare(email);
                    editText.setText("");
                    handled = true;
                }
                return handled;
            }
        });

        return new AlertDialog.Builder(getActivity())
                .setView(view)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mAdapter.filterDeltas();
                        getParent().persistence.shareTodoList(mAdded);
                        // TODO(alexfandrianto/rosswang): removal
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .create();
    }

    public void onSharedToChanged() {
        mContacts.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.setSharedTo(getParent().getSharedTo());
            }
        });
    }

    @Override
    public void onContactTouch(RecyclerView.ViewHolder viewHolder) {
        mAdapter.toggleContact(viewHolder.getAdapterPosition());
    }

    // Keep track of the Users we have found who are nearby.
    private static Set<User> mUsers = new HashSet<>();
    private static Set<ContactAdapter> mAdapters = new HashSet<>();

    public static synchronized void addUser(User user) {
        mUsers.add(user);
        for(ContactAdapter adapter : mAdapters) {
            adapter.onNearbyDeviceDiscovered(user.getAlias());
        }
    }
    public static synchronized void rmUser(User user) {
        mUsers.remove(user);
        for(ContactAdapter adapter : mAdapters) {
            adapter.onNearbyDeviceLost(user.getAlias());
        }
    }
    public static synchronized void addAdapter(ContactAdapter adapter) {
        mAdapters.add(adapter);
        for(User user : mUsers) {
            adapter.onNearbyDeviceDiscovered(user.getAlias());
        }
    }
    public static synchronized void rmAdapter(ContactAdapter adapter) {
        mAdapters.remove(adapter);
    }

    public static void initScan() {
        Syncbase.ScanNeighborhoodForUsersCallback mScanCb = new Syncbase.ScanNeighborhoodForUsersCallback() {
            @Override
            public void onFound(User user) {
                addUser(user);
            }

            @Override
            public void onLost(User user) {
                rmUser(user);
            }

            @Override
            public void onError(Throwable e) {
                Log.w(FRAGMENT_TAG, e);
            }
        };
        Syncbase.addScanForUsersInNeighborhood(mScanCb);
    }
}
