// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.v.todos.model.ListMetadata;

public final class UIUtil {
    private UIUtil() {
    }

    private static final long JUST_NOW_DURATION = 60 * 1000 - 1;

    public static String computeTimeAgo(Context context, String prefix, long startTime) {
        long now = System.currentTimeMillis();
        return prefix + ": " + (now - startTime > JUST_NOW_DURATION ?
                DateUtils.getRelativeTimeSpanString(startTime, now, DateUtils.MINUTE_IN_MILLIS) :
                context.getString(R.string.just_now));
    }

    private static AlertDialog lastDialog;

    public static AlertDialog getLastDialog() {
        return lastDialog;
    }

    public static void showAddDialog(Context context, String title,
                                     final DialogResponseListener addListener) {
        final EditText todoItem = new EditText(context);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(todoItem)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        addListener.handleResponse(todoItem.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        lastDialog = dialog;
    }

    public static void showEditDialog(Context context, String title, String defaultValue,
                                      final DialogResponseListener editListener) {
        final EditText todoItem = new EditText(context);
        todoItem.setText(defaultValue);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(todoItem)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        editListener.handleResponse(todoItem.getText().toString());
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        editListener.handleDelete();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        lastDialog = dialog;
    }

    public static abstract class DialogResponseListener {
        public abstract void handleResponse(String response);

        public void handleDelete() {
        }
    }
}
