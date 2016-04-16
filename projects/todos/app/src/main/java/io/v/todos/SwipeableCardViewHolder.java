// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public abstract class SwipeableCardViewHolder extends RecyclerView.ViewHolder {
    private CardView cardView;
    private View swipeLeftView;
    private View swipeRightView;

    public SwipeableCardViewHolder(View itemView) {
        super(itemView);

        cardView = (CardView)itemView.findViewById(R.id.card);
        swipeLeftView = itemView.findViewById(R.id.swipe_left);
        swipeRightView = itemView.findViewById(R.id.swipe_right);
    }

    public CardView getCardView() {
        return cardView;
    }

    public void decideBackground(float dX) {
        swipeLeftView.setVisibility(dX < 0 ? View.VISIBLE : View.GONE);
        swipeRightView.setVisibility(dX > 0 ? View.VISIBLE : View.GONE);
    }
}
