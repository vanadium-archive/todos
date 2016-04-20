// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import io.v.todos.model.Task;

/**
 * @author alexfandrianto
 */
public class TaskViewHolder extends SwipeableCardViewHolder {
    private boolean showDone = true;

    public TaskViewHolder(View itemView) {
        super(itemView);
    }

    public void bindTask(Task task, View.OnClickListener listener) {
        final ImageView doneMark = (ImageView) itemView.findViewById(R.id.task_done);
        doneMark.setVisibility(task.done ? View.VISIBLE : View.GONE);

        final TextView name=(TextView) itemView.findViewById(R.id.task_text);
        name.setText(task.text);

        final TextView created=(TextView) itemView.findViewById(R.id.task_time);
        created.setText(computeCreated(task));

        getCardView().setCardBackgroundColor(task.done ? 0xFFCCCCCC : 0xFFFFFFFF);

        itemView.setTag(task.key);
        itemView.setOnClickListener(listener);

        itemView.setVisibility(!showDone && task.done ? View.GONE : View.VISIBLE);
    }

    private String computeCreated(Task task) {
        return UIUtil.computeTimeAgo("Created", task.addedAt);
    }

    public void setShowDone(boolean showDone) {
        this.showDone = showDone;
    }
}
