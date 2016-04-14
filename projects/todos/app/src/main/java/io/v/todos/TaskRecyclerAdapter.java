// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Created by alexfandrianto on 4/7/16.
 */
public class TaskRecyclerAdapter extends RecyclerView.Adapter<TaskViewHolder> {
    private ArrayList<Task> backup;
    private View.OnClickListener itemListener;
    private boolean showDone = true;

    private static final int RESOURCE_ID = R.layout.task_row;

    public TaskRecyclerAdapter(ArrayList<Task> backup, View.OnClickListener itemListener) {
        super();
        this.backup = backup;
        this.itemListener = itemListener;
    }

    @Override
    public TaskViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(RESOURCE_ID, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TaskViewHolder holder, int position) {
        Task task = backup.get(position);
        holder.bindTask(task, itemListener);
    }

    @Override
    public int getItemCount() {
        return showDone ? backup.size() : nonDoneSize();
    }

    private int nonDoneSize() {
        for (int i = 0; i < backup.size(); i++) {
            if (backup.get(i).getDone()) {
                return i;
            }
        }
        return 0;
    }

    public void setShowDone(boolean showDone) {
        this.showDone = showDone;
        this.notifyDataSetChanged();
    }
}