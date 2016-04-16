// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.view.View;
import android.widget.TextView;

/**
 * @author alexfandrianto
 */
public class TodoListViewHolder extends SwipeableCardViewHolder {
    public TodoListViewHolder(View itemView) {
        super(itemView);
    }

    public void bindTodoList(TodoList todoList, View.OnClickListener listener) {
        final TextView name=(TextView) itemView.findViewById(R.id.todo_list_name);
        name.setText(todoList.getName());

        final TextView completedStatus=(TextView) itemView.findViewById(R.id.todo_list_completed);
        completedStatus.setText(computeCompleted(todoList));

        final TextView timeAgo=(TextView) itemView.findViewById(R.id.todo_list_time);
        timeAgo.setText(computeTimeAgo(todoList));

        getCardView().setCardBackgroundColor(todoList.getDone() ? 0xFFCCCCCC : 0xFFFFFFFF);

        itemView.setTag(todoList.getKey());
        itemView.setOnClickListener(listener);
    }

    private String computeTimeAgo(TodoList todoList) {
        return "" + todoList.getUpdatedAt();
    }

    private String computeCompleted(TodoList todoList) {
        if (todoList.getDone()) {
            return "Done!";
        } else if (todoList.numTasks == 0) {
            return "Needs Tasks";
        } else if (todoList.numCompleted == 0) {
            return "Not Started";
        } else {
            return todoList.numCompleted + " of " + todoList.numTasks;
        }
    }
}
