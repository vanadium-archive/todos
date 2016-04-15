// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author alexfandrianto
 */
public class TaskViewHolder extends RecyclerView.ViewHolder{
    private CardView myView;
    private boolean showDone = true;

    public TaskViewHolder(View itemView) {
        super(itemView);

        myView = (CardView)itemView;
    }

    public void bindTask(Task task, View.OnClickListener listener) {
        // TODO(alexfandrianto): Now might be a good time to set data in myView.

        final ImageView doneMark = (ImageView)myView.findViewById(R.id.task_done);
        doneMark.setVisibility(task.getDone() ? View.VISIBLE : View.GONE);

        final TextView name=(TextView)myView.findViewById(R.id.task_text);
        name.setText(task.getText());

        final TextView created=(TextView)myView.findViewById(R.id.task_time);
        created.setText(computeCreated(task));

        myView.setCardBackgroundColor(task.getDone() ? 0xFFCCCCCC : 0xFFFFFFFF);

        myView.setTag(task.getKey());
        myView.setOnClickListener(listener);

        myView.setVisibility(!showDone && task.getDone() ? View.GONE : View.VISIBLE);
    }

    private String computeCreated(Task task) {
        return "" + task.getAddedAt();
    }

    public void setShowDone(boolean showDone) {
        this.showDone = showDone;
    }
}
