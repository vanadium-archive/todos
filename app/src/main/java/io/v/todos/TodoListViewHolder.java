// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.graphics.Paint;
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
        if (listMetadata.isDone()) {
            mName.setPaintFlags(mName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            mName.setTextColor(mName.getTextColors().withAlpha(UIUtil.ALPHA_HINT));
        } else {
            mName.setPaintFlags(mName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            mName.setTextColor(mName.getTextColors().withAlpha(UIUtil.ALPHA_PRIMARY));
        }
        mTimeAgo.setText(computeTimeAgo(listMetadata));

        //getCardView().setCardBackgroundColor(listMetadata.isDone() ? 0xFFCCCCCC : 0xFFFFFFFF);

        itemView.setTag(listMetadata.key);
        itemView.setOnClickListener(listener);
    }

    private String computeTimeAgo(ListMetadata listMetadata) {
        return UIUtil.computeTimeAgo(getCardView().getContext(), listMetadata.updatedAt);
    }

    private String computeCompleted(ListMetadata listMetadata) {
        if (listMetadata.numTasks == 0) {
            return "No Tasks";
        } else {
            return listMetadata.numCompleted + "/" + listMetadata.numTasks + " completed";
        }
    }

    @Override
    public String toString() {
        return mName.getText() + " (" + mCompletedStatus.getText() + "), " + mTimeAgo.getText();
    }
}
