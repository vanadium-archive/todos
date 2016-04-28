// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toolbar;

import io.v.todos.persistence.Persistence;

public class TodosAppActivity<P extends Persistence, A extends RecyclerView.Adapter<?>>
        extends Activity {
    protected P mPersistence;
    protected A mAdapter;

    protected TextView mEmptyView;

    /**
     * Allow tests to mock out persistence.
     */
    @VisibleForTesting
    void setPersistence(P persistence) {
        mPersistence = persistence;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        mEmptyView = (TextView) findViewById(R.id.empty);
    }

    @Override
    protected void onDestroy() {
        if (mPersistence != null) {
            mPersistence.close();
            mPersistence = null;
        }
        super.onDestroy();
    }

    /**
     * Set the visibility based on what the adapter thinks is the visible item count.
     */
    protected void setEmptyVisiblity() {
        mEmptyView.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
