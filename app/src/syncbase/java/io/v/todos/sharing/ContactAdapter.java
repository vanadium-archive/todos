// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.sharing;

import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import io.v.todos.R;

public class ContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int
            VIEW_TYPE_SUBHEADER = 0,
            VIEW_TYPE_CONTACT = 1;

    public interface ContactTouchListener {
        void onContactTouch(RecyclerView.ViewHolder viewHolder);
    }

    private static class SubheaderViewHolder extends RecyclerView.ViewHolder {
        public final TextView category;

        public SubheaderViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.sharing_subheader, parent, false));
            category = (TextView) itemView.findViewById(R.id.category);
        }
    }

    private static class ContactViewHolder extends RecyclerView.ViewHolder {
        public final TextView name;

        public ContactViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.sharing_entry, parent, false));
            name = (TextView) itemView.findViewById(R.id.name);
        }
    }

    // TODO(rosswang): Save expanded state, and/or manage more intelligently.
    // TODO(rosswang): Potentially show condensed avatars in collapsed state.
    private static class Sublist {
        @StringRes
        public final int header;
        public final List<String> items = new ArrayList<>();

        public Sublist(@StringRes int header) {
            this.header = header;
        }

        public int getEffectiveSize() {
            return 1 + items.size();
        }
    }

    private final Sublist
            mSharingAlready = new Sublist(R.string.sharing_already),
            mSharingPossible = new Sublist(R.string.sharing_possible);

    private final Sublist[] mSections = new Sublist[]{
            mSharingAlready,
            mSharingPossible
    };

    private Collection<String> mSharedAlready;
    private final Set<String> mSharesAdded, mSharesRemoved, mSharesRecent;
    private final Multiset<String> mDiscoCounter = HashMultiset.create();
    private ContactTouchListener mContactTouchListener;

    public ContactAdapter(Collection<String> sharedAlready, Set<String> sharesAdded,
                          Set<String> sharesRemoved, Set<String> sharesRecent) {
        mSharesAdded = sharesAdded;
        mSharesRemoved = sharesRemoved;
        mSharesRecent = sharesRecent;

        Set<String> recentUnaccountedFor = new HashSet<>(sharesRecent);
        recentUnaccountedFor.removeAll(sharedAlready);
        recentUnaccountedFor.removeAll(sharesAdded);
        recentUnaccountedFor.removeAll(sharesRemoved);
        mSharingPossible.items.addAll(recentUnaccountedFor);
        mSharingPossible.items.addAll(sharesRemoved);
        Collections.sort(mSharingPossible.items);

        setSharedAlreadyData(sharedAlready);
    }

    private void setSharedAlreadyData(Collection<String> sharedAlready) {
        mSharedAlready = sharedAlready;
        mSharingAlready.items.clear();
        mSharingAlready.items.addAll(sharedAlready);
        mSharingAlready.items.removeAll(mSharesRemoved);
        mSharingAlready.items.addAll(mSharesAdded);
        Collections.sort(mSharingAlready.items);
    }

    public void filterDeltas() {
        mSharesAdded.removeAll(mSharedAlready);
        mSharesRemoved.retainAll(mSharedAlready);
    }

    private static class SublistEntry {
        public final Sublist sublist;
        public final int itemPosition;
        public final String item;

        public SublistEntry(Sublist sublist, int itemPosition, String item) {
            this.sublist = sublist;
            this.itemPosition = itemPosition;
            this.item = item;
        }
    }

    private SublistEntry getSublistItemPosition(int position) {
        for (Sublist section : mSections) {
            if (position == 0) {
                return new SublistEntry(section, -1, null);
            }
            position--;
            if (position < section.items.size()) {
                return new SublistEntry(section, position, section.items.get(position));
            }
            position -= section.items.size();
        }
        throw new IndexOutOfBoundsException("No sublist at position " + position);
    }

    /**
     * Inverse of {@link #getSublistItemPosition(int)}.
     */
    private int getViewPosition(Sublist section, int itemPosition) {
        int offset = 1;
        for (Sublist cursor: mSections) {
            if (cursor == section) {
                return offset + itemPosition;
            } else {
                offset += cursor.getEffectiveSize();
            }
        }
        throw new NoSuchElementException("Section is not in list");
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SUBHEADER) {
            return new SubheaderViewHolder(parent);
        } else {
            final ContactViewHolder cvh = new ContactViewHolder(parent);
            cvh.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mContactTouchListener.onContactTouch(cvh);
                }
            });
            return cvh;
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final SublistEntry entry = getSublistItemPosition(position);
        if (entry.item == null) {
            final SubheaderViewHolder svh = (SubheaderViewHolder) holder;
            svh.category.setText(entry.sublist.header);
        } else {
            final ContactViewHolder cvh = (ContactViewHolder) holder;
            cvh.name.setText(entry.item);
        }
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (Sublist section : mSections) {
            count += section.getEffectiveSize();
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        return getSublistItemPosition(position).item == null ?
                VIEW_TYPE_SUBHEADER : VIEW_TYPE_CONTACT;
    }

    public void setContactTouchListener(ContactTouchListener listener) {
        mContactTouchListener = listener;
    }

    public void onNearbyDeviceLost(String email) {
        // short-circuit note; remove must happen
        if (mDiscoCounter.remove(email, 1) > 1 ||
                Collections.binarySearch(mSharingAlready.items, email) >= 0) {
            return;
        }
        int i = Collections.binarySearch(mSharingPossible.items, email);
        if (i >= 0) {
            mSharingPossible.items.remove(i);
            notifyItemRemoved(getViewPosition(mSharingPossible, i));
        }
    }

    public void onNearbyDeviceDiscovered(String email) {
        // short-circuit note; add must happen
        if (mDiscoCounter.add(email, 1) > 0 ||
                Collections.binarySearch(mSharingAlready.items, email) >= 0) {
            return;
        }
        int i = Collections.binarySearch(mSharingPossible.items, email);
        if (i < 0) {
            i = ~i;
            mSharingPossible.items.add(i, email);
            notifyItemInserted(getViewPosition(mSharingPossible, i));
        }
    }

    private int shareWithPossible(int i) {
        String email = mSharingPossible.items.remove(i);
        // Animate movement by moving the item.
        int j = ~Collections.binarySearch(mSharingAlready.items, email);
        int oldPosition = getViewPosition(mSharingPossible, i);
        int newPosition = getViewPosition(mSharingAlready, j);
        mSharingAlready.items.add(j, email);
        notifyItemMoved(oldPosition, newPosition);
        registerShare(email);
        return newPosition;
    }

    private void unshare(int i) {
        String email = mSharingAlready.items.remove(i);
        int j = ~Collections.binarySearch(mSharingPossible.items, email);
        mSharingPossible.items.add(j, email);
        registerUnshare(email);

        // animate movement
        int oldPosition = getViewPosition(mSharingAlready, i);
        int newPosition = getViewPosition(mSharingPossible, j);
        notifyItemMoved(oldPosition, newPosition);
    }

    private int insertShare(String email) {
        int i = Collections.binarySearch(mSharingAlready.items, email);
        if (i >= 0) {
            return getViewPosition(mSharingAlready, i);
        } else {
            i = ~i;
            mSharingAlready.items.add(i, email);
            i = getViewPosition(mSharingAlready, i);
            notifyItemInserted(i);
            registerShare(email);
            return i;
        }
    }

    private void registerShare(String email) {
        mSharesRecent.add(email);
        if (!mSharesRemoved.remove(email)) {
            mSharesAdded.add(email);
        }
    }

    private void registerUnshare(String email) {
        if (!mSharesAdded.remove(email)) {
            mSharesRemoved.add(email);
        }
    }

    /**
     * @return the position of the e-mail in the adapter
     */
    public int onCustomShare(String email) {
        int i = Collections.binarySearch(mSharingPossible.items, email);
        if (i >= 0) {
            return shareWithPossible(i);
        } else {
            return insertShare(email);
        }
    }

    public void setSharedTo(Collection<String> sharedAlready) {
        setSharedAlreadyData(sharedAlready);
        // TODO(rosswang): list differ

        notifyDataSetChanged();
    }

    // TODO(rosswang): this is a hacky abstraction
    public void toggleContact(int position) {
        SublistEntry entry = getSublistItemPosition(position);
        if (entry.sublist == mSharingAlready) {
            unshare(entry.itemPosition);
        } else {
            shareWithPossible(entry.itemPosition);
        }
    }
}