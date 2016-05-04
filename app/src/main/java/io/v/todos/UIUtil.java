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

    // The share dialog contains two recycler views. The top one shows the existing items, and the
    // bottom shows ones that are nearby but not shared to yet. There's also a freeform text box
    // to allow entry of any value.
    // Confirming this dialog sends the set of added and removed emails. Tap to add/remove.
    public static void showShareDialog(Context context, List<String> existing, List<String> nearby,
                                       final ShareDialogResponseListener shareListener) {
        final Set<String> emailsRemoved = new HashSet<>();
        final Set<String> emailsAdded = new HashSet<>();
        final List<String> manualEmailsTyped = new ArrayList<>();

        View sharingView = View.inflate(context, R.layout.sharing, null);

        final RecyclerView rvAlready = (RecyclerView) sharingView.findViewById(R.id
                .recycler_already);
        rvAlready.setAdapter(new ContactAdapter(existing, emailsRemoved, true));
        final RecyclerView rvPossible = (RecyclerView) sharingView.findViewById(R.id
                .recycler_possible);
        rvPossible.setAdapter(new ContactAdapter(nearby, manualEmailsTyped, emailsAdded, false));

        final EditText editText = (EditText) sharingView.findViewById(R.id.custom_email);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String email = editText.getText().toString();
                    if (!manualEmailsTyped.contains(email)) {
                        manualEmailsTyped.add(email);
                    }
                    emailsAdded.add(email);
                    rvPossible.getAdapter().notifyDataSetChanged();
                    editText.setText("");
                    handled = true;
                }
                return handled;
            }
        });

        new AlertDialog.Builder(context)
                .setView(sharingView)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        shareListener.handleShareChanges(emailsAdded, emailsRemoved);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
    }

    public interface ShareDialogResponseListener {
        void handleShareChanges(Set<String> emailsAdded, Set<String> emailsRemoved);
    }

    private static class ContactAdapter extends RecyclerView.Adapter<ContactViewHolder> {
        private final List<String> backup;
        private final List<String> bonus;
        private final Set<String> toggledOn;
        private final boolean strikethrough; // If false, then bold.

        public ContactAdapter(List<String> backup, Set<String> toggledOn, boolean strikethrough) {
            super();
            this.backup = backup;
            this.bonus = null;
            this.toggledOn = toggledOn;
            this.strikethrough = strikethrough;
        }

        public ContactAdapter(List<String> backup, List<String> bonus, Set<String> toggledOn,
                              boolean strikethrough) {
            super();
            this.backup = backup;
            this.bonus = bonus;
            this.toggledOn = toggledOn;
            this.strikethrough = strikethrough;
        }

        @Override
        public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ContactViewHolder(new TextView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(final ContactViewHolder holder, int position) {
            final String name = position < backup.size() ? backup.get(position) :
                    bonus.get(position - backup.size());
            final boolean present = toggledOn.contains(name);
            holder.bindString(name, present, strikethrough, new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    if (present) {
                        toggledOn.remove(name);
                    } else {
                        toggledOn.add(name);
                    }
                    notifyItemChanged(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            int extra = bonus == null ? 0 : bonus.size();
            return backup.size() + extra;
        }
    }

    private static class ContactViewHolder extends RecyclerView.ViewHolder {
        public ContactViewHolder(View itemView) {
            super(itemView);
        }

        public void bindString(String name, boolean isActive, boolean strikethrough, View
                .OnClickListener listener) {
            TextView text = (TextView) itemView;

            text.setText(name);
            text.setTextSize(18);
            if (strikethrough) {
                if (isActive) {
                    text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    text.setPaintFlags(text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }
            } else {
                // We should bold!
                if (isActive) {
                    text.setTypeface(null, 1); // 1 is bold
                } else {
                    text.setTypeface(null, 0); // 0 is default text style
                }
            }

            text.setOnClickListener(listener);
        }
    }
}
