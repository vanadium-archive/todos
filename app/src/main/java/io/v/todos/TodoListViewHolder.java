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
    private final TextView mName, mCompletedStatus, mTimeAgo;

    public TodoListViewHolder(View itemView) {
        super(itemView);
        mName = (TextView) itemView.findViewById(R.id.todo_list_name);
        mCompletedStatus = (TextView) itemView.findViewById(R.id.todo_list_completed);
        mTimeAgo = (TextView) itemView.findViewById(R.id.todo_list_time);
    }

    public void bindTodoList(ListMetadata listMetadata, View.OnClickListener listener) {
        mName.setText(listMetadata.name);
        mCompletedStatus.setText(computeCompleted(listMetadata));
        mTimeAgo.setText(computeTimeAgo(listMetadata));

        getCardView().setCardBackgroundColor(listMetadata.isDone() ? 0xFFCCCCCC : 0xFFFFFFFF);

        itemView.setTag(listMetadata.key);
        itemView.setOnClickListener(listener);
    }

    private String computeTimeAgo(ListMetadata listMetadata) {
        return UIUtil.computeTimeAgo("Last Updated", listMetadata.updatedAt);
    }

    private String computeCompleted(ListMetadata listMetadata) {
        if (listMetadata.isDone()) {
            return "Done!";
        } else if (listMetadata.numTasks == 0) {
            return "Needs Tasks";
        } else if (listMetadata.numCompleted == 0) {
            return "Not Started";
        } else {
            return listMetadata.numCompleted + " of " + listMetadata.numTasks;
        }
    }

    @Override
    public String toString() {
        return mName.getText() + " (" + mCompletedStatus.getText() + "), " + mTimeAgo.getText();
    }
}
