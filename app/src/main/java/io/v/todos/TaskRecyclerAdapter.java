// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import io.v.todos.model.Task;

/**
 * @author alexfandrianto
 */
public class TaskRecyclerAdapter extends RecyclerView.Adapter<TaskViewHolder> {
    private ArrayList<Task> mBackup;
    private View.OnClickListener mItemListener;
    private View.OnClickListener mDoneListener;
    private boolean mShowDone = true;

    private static final int RESOURCE_ID = R.layout.task_row;

    public TaskRecyclerAdapter(ArrayList<Task> backup, View.OnClickListener itemListener,
                               View.OnClickListener doneListener) {
        super();
        mBackup = backup;
        mItemListener = itemListener;
        mDoneListener = doneListener;
    }

    @Override
    public TaskViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(RESOURCE_ID, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TaskViewHolder holder, int position) {
        Task task = mBackup.get(position);
        holder.bindTask(task, mItemListener, mDoneListener);
    }

    @Override
    public int getItemCount() {
        return mShowDone ? mBackup.size() : nonDoneSize();
    }

    private int nonDoneSize() {
        for (int i = 0; i < mBackup.size(); i++) {
            if (mBackup.get(i).done) {
                return i;
            }
        }
        return mBackup.size();
    }

    public void setShowDone(boolean showDone) {
        if (mShowDone != showDone) {
            mShowDone = showDone;
            int nonDoneSize = nonDoneSize(),
                doneSize = mBackup.size() - nonDoneSize;

            if (showDone) {
                notifyItemRangeInserted(nonDoneSize, doneSize);
            } else {
                notifyItemRangeRemoved(nonDoneSize, doneSize);
            }
        }
    }

    public boolean getShowDone() {
        return mShowDone;
    }
}