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
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import io.v.todos.R;
import io.v.todos.persistence.syncbase.SyncbasePersistence;
import io.v.v23.InputChannelCallback;
import io.v.v23.InputChannels;
import io.v.v23.context.VContext;
import io.v.v23.discovery.Update;
import io.v.v23.verror.VException;

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

    private VContext mScanContext;

    private ShareListMenuFragment getParent() {
        return ((ShareListMenuFragment) getParentFragment());
    }

    @Override
    public void onDestroyView() {
        mScanContext.cancel();
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
        mContacts.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean animateAdd(RecyclerView.ViewHolder holder) {
                dispatchAddFinished(holder);
                return false;
            }

            @Override
            public boolean animateRemove(RecyclerView.ViewHolder holder) {
                dispatchRemoveFinished(holder);
                return false;
            }
        });

        mScanContext = SyncbasePersistence.getAppVContext().withCancel();
        try {
            ListenableFuture<Void> scan = InputChannels.withCallback(
                    Sharing.getDiscovery().scan(mScanContext,
                            "v.InterfaceName = \"" + Sharing.getPresenceInterface() + "\""),
                    new InputChannelCallback<Update>() {
                        @Override
                        public ListenableFuture<Void> onNext(Update result) {
                            final String email = Iterables.getOnlyElement(result.getAddresses());
                            if (email == null ||
                                    email.equals(SyncbasePersistence.getPersonalEmail())) {
                                return null;
                            }
                            if (result.isLost()) {
                                mAdapter.onNearbyDeviceLost(email);
                            } else {
                                mAdapter.onNearbyDeviceDiscovered(email);
                            }
                            return null;
                        }
                    });
            Futures.addCallback(scan, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                }

                @Override
                public void onFailure(Throwable t) {
                    handleScanningError(t);
                }
            });
        } catch (VException e) {
            handleScanningError(e);
        }

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

    private void handleScanningError(Throwable t) {
        //TODO(rosswang): indicate error in the view
        SyncbasePersistence.getAppErrorReporter().onError(R.string.err_scan_nearby, t);
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
}
