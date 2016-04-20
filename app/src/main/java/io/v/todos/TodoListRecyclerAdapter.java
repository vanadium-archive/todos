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
    private ArrayList<ListMetadata> backup;
    private View.OnClickListener itemListener;

    private static final int RESOURCE_ID = R.layout.todo_list_row;

    public TodoListRecyclerAdapter(ArrayList<ListMetadata> backup, View.OnClickListener itemListener) {
        super();
        this.backup = backup;
        this.itemListener = itemListener;
    }

    @Override
    public TodoListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(RESOURCE_ID, parent, false);
        return new TodoListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TodoListViewHolder holder, int position) {
        ListMetadata listMetadata = backup.get(position);
        holder.bindTodoList(listMetadata, itemListener);
    }

    @Override
    public int getItemCount() {
        return backup.size();
    }
}