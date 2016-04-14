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
public class TodoListRecyclerAdapter extends RecyclerView.Adapter<TodoListViewHolder> {
    private ArrayList<TodoList> backup;
    private View.OnClickListener itemListener;

    private static final int RESOURCE_ID = R.layout.todo_list_row;

    public TodoListRecyclerAdapter(ArrayList<TodoList> backup, View.OnClickListener itemListener) {
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
        TodoList todoList = backup.get(position);
        holder.bindTodoList(todoList, itemListener);
    }

    @Override
    public int getItemCount() {
        return backup.size();
    }
}
