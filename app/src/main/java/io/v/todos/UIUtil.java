// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.support.design.widget.TextInputLayout;
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


    private static final long JUST_NOW_DURATION = 60 * 60 * 1000 - 1;
    public static final int ALPHA_PRIMARY = (int)(255 * 0.87);
    public static final int ALPHA_SECONDARY = (int)(255 * 0.54);
    public static final int ALPHA_HINT = (int)(255 * 0.38);

    public static String computeTimeAgo(Context context, long startTime) {
        long now = System.currentTimeMillis();
        // TODO(alexfandrianto): We could use even shorter strings for times.
        return (now - startTime > JUST_NOW_DURATION ?
                DateUtils.getRelativeTimeSpanString(startTime, now, DateUtils.HOUR_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_ALL).toString() :
                context.getString(R.string.just_now));
    }

    private static AlertDialog lastDialog;

    public static AlertDialog getLastDialog() {
        return lastDialog;
    }

    public static AlertDialog dialogMaker(Context context, String title, String defaultValue,
                                             final DialogResponseListener listener) {
        // Prepare the edit text.
        TextInputLayout inputLayout = (TextInputLayout)LayoutInflater.from(context).
                inflate(R.layout.dialog_edittext, null);
        final EditText editText = inputLayout.getEditText();
        boolean adding = (defaultValue == null);
        if (!adding) {
            editText.setText(defaultValue);
        }

        // Build the alert dialog.
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(inputLayout)
                .setPositiveButton(adding ? "Add" : "Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String response = editText.getText().toString();
                        if (response != null && response.length() > 0) {
                            listener.handleResponse(response);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                });
        if (!adding) {
            // Only items being edited can be deleted.
            // TODO(alexfandrianto): Should we keep this option? We can also swipe in order to
            // delete tasks/lists.
            dialogBuilder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    listener.handleDelete();
                }
            });
        }

        // Show the dialog with the keyboard up. If the "Send" button is pressed, treat that as a
        // positive button press.
        final AlertDialog dialog = dialogBuilder.show();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) ||
                        (actionId == EditorInfo.IME_ACTION_DONE)) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            }
        });

        return dialog;
    }

    public static void showAddDialog(Context context, String title,
                                     final DialogResponseListener addListener) {
        AlertDialog dialog = dialogMaker(context, title, null, addListener);

        lastDialog = dialog;
    }

    public static void showEditDialog(Context context, String title, String defaultValue,
                                      final DialogResponseListener editListener) {

        AlertDialog dialog = dialogMaker(context, title, defaultValue, editListener);

        lastDialog = dialog;
    }

    public static abstract class DialogResponseListener {
        public abstract void handleResponse(String response);

        public void handleDelete() {
        }
    }
}
