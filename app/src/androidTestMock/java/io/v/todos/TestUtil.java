// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.EditText;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static junit.framework.Assert.assertTrue;

class TestUtil {
    static final String KEY1 = "asdlkjweriousdf";
    static final String KEY2 = "woeiuflskjeroius";
    static final String NAME1 = "weoislkjdflkejroif";
    static final String NAME2 = "weurlksdnoielrkmlsd";
    static final String NAME3 = "oisdlkwejrllisdfelkejr";

    static final String TEST_LIST_KEY = "list key";
    static final String TEST_LIST_NAME = "list name";

    private static final long UI_DELAY = 500; // Tweak this value to adjust the test speed.

    static void pause() {
        try {
            Thread.sleep(UI_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static AlertDialog beginAddItem(Activity activity) {
        onView(withId(R.id.fab)).perform(click());
        pause();

        final AlertDialog dialog = UIUtil.getLastDialog();

        assertTrue(dialog.isShowing());

        activity.runOnUiThread(new Runnable() {
            public void run() {
                EditText et = (EditText) dialog.getCurrentFocus();
                et.setText("HELLO WORLD!");
            }
        });

        pause();

        return dialog;
    }

    static AlertDialog handleEditDialog(Activity activity) {
        final AlertDialog dialog = UIUtil.getLastDialog();

        assertTrue(dialog.isShowing());

        activity.runOnUiThread(new Runnable() {
            public void run() {
                EditText et = (EditText) dialog.getCurrentFocus();
                et.setText("HELLO WORLD!");
            }
        });

        pause();

        return dialog;
    }
}
