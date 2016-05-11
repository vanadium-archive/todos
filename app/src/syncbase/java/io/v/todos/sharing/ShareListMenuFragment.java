// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.sharing;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

import io.v.todos.R;
import io.v.todos.persistence.syncbase.SyncbaseTodoList;

/**
 * In addition to providing the menu item, this class brokers interaction between
 * {@link SyncbaseTodoList} and {@link ShareListDialogFragment}.
 */
public class ShareListMenuFragment extends Fragment {
    public static final String FRAGMENT_TAG = ShareListMenuFragment.class.getSimpleName();

    public static ShareListMenuFragment find(FragmentManager mgr) {
        return (ShareListMenuFragment) mgr.findFragmentByTag(FRAGMENT_TAG);
    }

    public SyncbaseTodoList persistence;

    private List<String> mSharedTo = new ArrayList<>();

    public void setSharedTo(List<String> sharedTo) {
        mSharedTo = sharedTo;

        ShareListDialogFragment dialog = ShareListDialogFragment.find(getChildFragmentManager());
        if (dialog != null) {
            dialog.onSharedToChanged();
        }
    }

    public List<String> getSharedTo() {
        return mSharedTo;
    }

    private String mEmail;
    public void setEmail(String email) {
        mEmail = email;
    }
    public String getEmail() {
        return mEmail;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_share, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                new ShareListDialogFragment()
                        .show(getChildFragmentManager(), ShareListDialogFragment.FRAGMENT_TAG);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
