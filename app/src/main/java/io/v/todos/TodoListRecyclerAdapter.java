// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import io.v.todos.model.ListMetadata;

/**
 * @author alexfandrianto
 */
public class TodoListRecyclerAdapter extends RecyclerView.Adapter<TodoListViewHolder> {
    private ArrayList<ListMetadata> mBackup;
    private View.OnClickListener mItemListener;

    private static final int RESOURCE_ID = R.layout.todo_list_row;

    public TodoListRecyclerAdapter(ArrayList<ListMetadata> backup, View.OnClickListener itemListener) {
        super();
        this.mBackup = backup;
        this.mItemListener = itemListener;
    }

    @Override
    public TodoListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(RESOURCE_ID, parent, false);
        return new TodoListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TodoListViewHolder holder, int position) {
        ListMetadata listMetadata = mBackup.get(position);
        holder.bindTodoList(listMetadata, mItemListener);
    }

    @Override
    public int getItemCount() {
        return mBackup.size();
    }
}
