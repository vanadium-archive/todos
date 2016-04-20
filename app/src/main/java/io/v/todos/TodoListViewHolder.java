// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.view.View;
import android.widget.TextView;

import io.v.todos.model.ListMetadata;

/**
 * @author alexfandrianto
 */
public class TodoListViewHolder extends SwipeableCardViewHolder {
    public TodoListViewHolder(View itemView) {
        super(itemView);
    }

    public void bindTodoList(ListMetadata listMetadata, View.OnClickListener listener) {
        final TextView name=(TextView) itemView.findViewById(R.id.todo_list_name);
        name.setText(listMetadata.getName());

        final TextView completedStatus=(TextView) itemView.findViewById(R.id.todo_list_completed);
        completedStatus.setText(computeCompleted(listMetadata));

        final TextView timeAgo=(TextView) itemView.findViewById(R.id.todo_list_time);
        timeAgo.setText(computeTimeAgo(listMetadata));

        getCardView().setCardBackgroundColor(listMetadata.getDone() ? 0xFFCCCCCC : 0xFFFFFFFF);

        itemView.setTag(listMetadata.getKey());
        itemView.setOnClickListener(listener);
    }

    private String computeTimeAgo(ListMetadata listMetadata) {
        return UIUtil.computeTimeAgo("Last Updated", listMetadata.getUpdatedAt());
    }

    private String computeCompleted(ListMetadata listMetadata) {
        if (listMetadata.getDone()) {
            return "Done!";
        } else if (listMetadata.numTasks == 0) {
            return "Needs Tasks";
        } else if (listMetadata.numCompleted == 0) {
            return "Not Started";
        } else {
            return listMetadata.numCompleted + " of " + listMetadata.numTasks;
        }
    }
}
