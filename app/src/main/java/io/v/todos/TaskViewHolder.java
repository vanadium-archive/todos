// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.graphics.Paint;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import io.v.todos.model.Task;

/**
 * @author alexfandrianto
 */
public class TaskViewHolder extends SwipeableCardViewHolder {
    public TaskViewHolder(View itemView) {
        super(itemView);
    }

    public void bindTask(Task task, View.OnClickListener itemListener,
                         View.OnClickListener doneListener) {
        final ImageButton doneMark = (ImageButton) itemView.findViewById(R.id.task_done);
        doneMark.setImageResource(task.done ? R.drawable.ic_check_box_black_24dp :
                R.drawable.ic_check_box_outline_blank_black_24dp);
        doneMark.setTag(task.key);
        doneMark.setOnClickListener(doneListener);

        final TextView name=(TextView) itemView.findViewById(R.id.task_text);
        name.setText(task.text);
        if (task.done) {
            name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(name.getTextColors().withAlpha(UIUtil.ALPHA_HINT));
        } else {
            name.setPaintFlags(name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(name.getTextColors().withAlpha(UIUtil.ALPHA_PRIMARY));
        }

        final TextView created=(TextView) itemView.findViewById(R.id.task_time);
        created.setText(computeCreated(task));

        itemView.setTag(task.key);
        itemView.setOnClickListener(itemListener);
    }

    private String computeCreated(Task task) {
        return UIUtil.computeTimeAgo(getCardView().getContext(), task.addedAt);
    }
}
