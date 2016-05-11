// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.sharing;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public class ShareListDialogFragment extends DialogFragment {
    public static final String FRAGMENT_TAG = ShareListDialogFragment.class.getSimpleName();

    public static ShareListDialogFragment find(FragmentManager fragmentManager) {
        return (ShareListDialogFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
    }

    private Set<String> mRemoved;
    private List<String> mNearby = new ArrayList<>();
    private ArrayList<String> mTyped;
    private Set<String> mAdded;

    private static final String
            REMOVED_KEY = "removedShares",
            TYPED_KEY = "explicitShares",
            ADDED_KEY = "addedShares";

    private ContactAdapter mAlreadyAdapter, mPossibleAdapter;

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
        outState.putStringArrayList(TYPED_KEY, mTyped);
        outState.putStringArrayList(ADDED_KEY, new ArrayList<>(mAdded));
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getActivity().getLayoutInflater().inflate(R.layout.sharing, null);

        if (savedInstanceState == null) {
            mRemoved = new HashSet<>();
            mAdded = new HashSet<>();
            mTyped = new ArrayList<>();
        } else {
            mRemoved = new HashSet<>(savedInstanceState.getStringArrayList(REMOVED_KEY));
            mAdded = new HashSet<>(savedInstanceState.getStringArrayList(ADDED_KEY));
            mTyped = savedInstanceState.getStringArrayList(TYPED_KEY);
        }

        mAlreadyAdapter = new ContactAdapter(getParent().getSharedTo(), mRemoved, true);
        RecyclerView rvAlready = (RecyclerView) view.findViewById(R.id.recycler_already);
        rvAlready.setAdapter(mAlreadyAdapter);
        mNearby.clear();
        final RecyclerView rvPossible = (RecyclerView) view.findViewById(R.id.recycler_possible);
        mPossibleAdapter = new ContactAdapter(mNearby, mTyped, mAdded, false);
        rvPossible.setAdapter(mPossibleAdapter);

        mScanContext = SyncbasePersistence.getAppVContext().withCancel();
        try {
            ListenableFuture<Void> scan = InputChannels.withCallback(
                    Sharing.getDiscovery().scan(mScanContext,
                            "v.InterfaceName = \"" + Sharing.getPresenceInterface() + "\""),
                    new InputChannelCallback<Update>() {
                        private final Map<String, Integer> counterMap = new HashMap<>();

                        @Override
                        public ListenableFuture<Void> onNext(Update result) {
                            final String email = Iterables.getOnlyElement(result.getAddresses());
                            if (email == null) {
                                return null;
                            }
                            // Note: binarySearch returns -|correct insert index| - 1 if it fails
                            // to find a match. For Java ints, this is the bitwise complement of the
                            // "correct" insertion index.
                            int searchIndex = Collections.binarySearch(mNearby, email);
                            if (result.isLost()) {
                                Integer old = counterMap.get(email);
                                counterMap.put(email, old == null ? 0 : Math.max(0, counterMap
                                        .get(email) - 1));
                                // Remove the email if the counter indicates that we should.
                                if (counterMap.get(email) == 0 && searchIndex >= 0) {
                                    mNearby.remove(searchIndex);
                                    mPossibleAdapter.notifyItemRemoved(searchIndex);
                                }
                            } else {
                                Integer old = counterMap.get(email);
                                counterMap.put(email, old == null ? 1 : counterMap.get(email) + 1);
                                // Show the email if it's a new one and not equal to our email.
                                // TODO(alexfandrianto): This still lets you see emails of those
                                // nearby who you've already invited.
                                if (searchIndex < 0 && !email.equals(getParent().getEmail())) {
                                    int insertIndex = ~searchIndex;
                                    mNearby.add(insertIndex, email);
                                    //mNearby.add(email);
                                    mPossibleAdapter.notifyItemInserted(insertIndex);
                                }
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
                    if (!mTyped.contains(email)) {
                        mTyped.add(email);
                    }
                    mAdded.add(email);
                    rvPossible.getAdapter().notifyDataSetChanged();
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
        //TODO(rosswang)
    }

    private static class ContactAdapter extends RecyclerView.Adapter<ContactViewHolder> {
        private final List<String> backup;
        private final List<String> bonus;
        private final Set<String> toggledOn;
        private final boolean strikethrough; // If false, then bold.

        public ContactAdapter(List<String> backup, Set<String> toggledOn, boolean strikethrough) {
            super();
            this.backup = backup;
            this.bonus = null;
            this.toggledOn = toggledOn;
            this.strikethrough = strikethrough;
        }

        public ContactAdapter(List<String> backup, List<String> bonus, Set<String> toggledOn,
                              boolean strikethrough) {
            super();
            this.backup = backup;
            this.bonus = bonus;
            this.toggledOn = toggledOn;
            this.strikethrough = strikethrough;
        }

        @Override
        public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ContactViewHolder(new TextView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(final ContactViewHolder holder, int position) {
            final String name = position < backup.size() ? backup.get(position) :
                    bonus.get(position - backup.size());
            final boolean present = toggledOn.contains(name);
            holder.bindString(name, present, strikethrough, new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    if (present) {
                        toggledOn.remove(name);
                    } else {
                        toggledOn.add(name);
                    }
                    notifyItemChanged(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            int extra = bonus == null ? 0 : bonus.size();
            return backup.size() + extra;
        }
    }

    private static class ContactViewHolder extends RecyclerView.ViewHolder {
        public ContactViewHolder(View itemView) {
            super(itemView);
        }

        public void bindString(String name, boolean isActive, boolean strikethrough, View
                .OnClickListener listener) {
            TextView text = (TextView) itemView;

            text.setText(name);
            text.setTextSize(18);
            if (strikethrough) {
                if (isActive) {
                    text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    text.setPaintFlags(text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }
            } else {
                // We should bold!
                if (isActive) {
                    text.setTypeface(null, 1); // 1 is bold
                } else {
                    text.setTypeface(null, 0); // 0 is default text style
                }
            }

            text.setOnClickListener(listener);
        }
    }
}
