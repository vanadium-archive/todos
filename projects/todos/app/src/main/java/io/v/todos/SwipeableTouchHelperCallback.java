// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.graphics.Canvas;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

/**
 * SwipeableTouchHelperCallback wraps the SimpleCallback for SwipeableCardViewHolders.
 * Subclasses should only override the onSwiped method since onMove is disabled.
 */
public abstract class SwipeableTouchHelperCallback extends ItemTouchHelper.SimpleCallback {
    SwipeableTouchHelperCallback() {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
              ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
    }

    @Override
    public boolean onMove(final RecyclerView recyclerView,
                          final RecyclerView.ViewHolder viewHolder,
                          final RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        SwipeableCardViewHolder holder = (SwipeableCardViewHolder)viewHolder;
        holder.decideBackground(0);
        getDefaultUIUtil().clearView(holder.getCardView());
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (viewHolder != null) {
            getDefaultUIUtil().onSelected(((SwipeableCardViewHolder) viewHolder).getCardView());
        }
    }

    public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        SwipeableCardViewHolder holder = (SwipeableCardViewHolder)viewHolder;
        holder.decideBackground(dX);
        getDefaultUIUtil().onDraw(c, recyclerView, holder.getCardView(), dX, dY, actionState,
                isCurrentlyActive);
    }

    public void onChildDrawOver(Canvas c, RecyclerView recyclerView,
                                RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {
        SwipeableCardViewHolder holder = (SwipeableCardViewHolder)viewHolder;
        getDefaultUIUtil().onDrawOver(c, recyclerView, holder.getCardView(), dX, dY, actionState,
                isCurrentlyActive);
    }
}
