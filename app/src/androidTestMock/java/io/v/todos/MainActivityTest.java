// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.DialogInterface;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.espresso.intent.Intents;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;

import io.v.todos.model.ListMetadata;
import io.v.todos.model.ListSpec;
import io.v.todos.persistence.ListEventListener;
import io.v.todos.persistence.MainPersistence;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Contains UI tests for the MainActivity that isolate the MainPersistence's behavior.
 * <p/>
 * Note: These tests will fail if the device's screen is off.
 * TODO(alexfandrianto): There do seem to be ways to force the screen to turn on though.
 * <p/>
 * Most sleeps are present in order to make the tests easier to follow when running.
 * <p/>
 * Created by alexfandrianto on 4/21/16.
 */
public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private static final String KEY1 = "asdlkjweriousdf";
    private static final String KEY2 = "woeiuflskjeroius";
    private static final String NAME1 = "weoislkjdflkejroif";
    private static final String NAME2 = "weurlksdnoielrkmlsd";
    private static final String NAME3 = "oisdlkwejrllisdfelkejr";
    private static final long UI_DELAY = 500; // Tweak this value to adjust the test speed.

    private MainActivity mActivity;

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Certain methods must be called before getActivity. Surprise!
        setActivityInitialTouchMode(true);

        mActivity = getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (mActivity != null) {
            mActivity.finish();
            mActivity = null;
        }
    }

    private void pause() {
        try {
            Thread.sleep(UI_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private MainPersistence mockPersistence() {
        MainPersistence mocked = mock(MainPersistence.class);
        mActivity.setMainPersistence(mocked);
        return mocked;
    }

    private AlertDialog beginAddItem() {
        onView(withId(R.id.fab)).perform(click());
        pause();

        final AlertDialog dialog = UIUtil.getLastDialog();

        assertTrue(dialog.isShowing());

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                EditText et = (EditText) dialog.getCurrentFocus();
                et.setText("HELLO WORLD!");
            }
        });

        pause();

        return dialog;
    }

    // Press the fab to attempt to add an item.
    public void testAttemptAddItem() {
        MainPersistence mocked = mockPersistence();
        AlertDialog dialog = beginAddItem();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();

        pause();

        assertFalse(dialog.isShowing());

        verify(mocked).addTodoList(any(ListSpec.class));
        verify(mocked, never()).deleteTodoList(anyString());
        verify(mocked, never()).completeAllTasks(any(ListMetadata.class));
    }

    // Press the fab but don't actually add the item.
    public void testAttemptAddItemFail() {
        MainPersistence mocked = mockPersistence();
        AlertDialog dialog = beginAddItem();

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).callOnClick();

        pause();

        assertFalse(dialog.isShowing());

        verify(mocked, never()).addTodoList(any(ListSpec.class));
        verify(mocked, never()).deleteTodoList(anyString());
        verify(mocked, never()).completeAllTasks(any(ListMetadata.class));
    }

    // Press the fab but don't actually add the item.
    public void testAttemptAddItemCancel() {
        MainPersistence mocked = mockPersistence();
        AlertDialog dialog = beginAddItem();

        dialog.dismiss();

        pause();

        assertFalse(dialog.isShowing());

        verify(mocked, never()).addTodoList(any(ListSpec.class));
        verify(mocked, never()).deleteTodoList(anyString());
        verify(mocked, never()).completeAllTasks(any(ListMetadata.class));
    }

    // Add some default items so that we can interact with them with swipes.
    private void addInitialData() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                ListEventListener<ListMetadata> listener = mActivity.createMainListener();
                listener.onItemAdd(new ListMetadata(KEY1, NAME1, 0, 0, 0));
                listener.onItemAdd(new ListMetadata(KEY2, NAME2, 1, 1, 2));
            }
        });
    }

    // Call the listener directly to add an item.
    public void testDirectlyAddItem() {
        final ListEventListener<ListMetadata> listener = mActivity.createMainListener();

        RecyclerView recycler = (RecyclerView) mActivity.findViewById(R.id.recycler);
        pause();

        final ListMetadata data = new ListMetadata(KEY1, NAME1, 0, 0, 0);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onItemAdd(data);
            }
        });

        pause();
        assertEquals(recycler.getAdapter().getItemCount(), 1);

        final ListMetadata data2 = new ListMetadata(KEY2, NAME2, 1, 1, 2);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onItemAdd(data2);
            }
        });

        pause();
        assertEquals(recycler.getAdapter().getItemCount(), 2);
    }

    public void testDirectlyEditItem() {
        addInitialData();

        pause();

        RecyclerView recycler = (RecyclerView) mActivity.findViewById(R.id.recycler);

        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME1))));
        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME2))));
        onView(withId(R.id.recycler)).check(matches(not(hasDescendant(withText(NAME3)))));

        final ListEventListener<ListMetadata> listener = mActivity.createMainListener();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onItemUpdate(new ListMetadata(KEY1, NAME3, 2, 0, 0));
            }
        });

        pause();

        onView(withId(R.id.recycler)).check(matches(not(hasDescendant(withText(NAME1)))));
        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME2))));
        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME3))));
        assertEquals(recycler.getAdapter().getItemCount(), 2);
    }

    public void testDirectlyDeleteItem() {
        addInitialData();

        pause();

        RecyclerView recycler = (RecyclerView) mActivity.findViewById(R.id.recycler);

        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME1))));
        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME2))));
        onView(withId(R.id.recycler)).check(matches(not(hasDescendant(withText(NAME3)))));

        final ListEventListener<ListMetadata> listener = mActivity.createMainListener();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onItemDelete(KEY2);
            }
        });

        pause();

        onView(withId(R.id.recycler)).check(matches(hasDescendant(withText(NAME1))));
        onView(withId(R.id.recycler)).check(matches(not(hasDescendant(withText(NAME2)))));
        onView(withId(R.id.recycler)).check(matches(not(hasDescendant(withText(NAME3)))));
        assertEquals(recycler.getAdapter().getItemCount(), 1);
    }

    // Swipe a todo list item to the right to mark all of its children as done.
    public void testAttemptCompleteAllTasks() {
        MainPersistence mocked = mockPersistence();
        addInitialData();

        pause();

        // DO UI INTERACTION
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, swipeRight()));

        pause();

        verify(mocked, never()).addTodoList(any(ListSpec.class));
        verify(mocked, never()).deleteTodoList(anyString());
        verify(mocked).completeAllTasks(any(ListMetadata.class));
    }

    // Swipe a todo list item to the left to attempt to delete it.
    public void testAttemptDeleteItem() {
        MainPersistence mocked = mockPersistence();
        addInitialData();

        pause();

        // DO UI INTERACTION
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, swipeLeft()));

        pause();

        verify(mocked, never()).addTodoList(any(ListSpec.class));
        verify(mocked).deleteTodoList(anyString());
        verify(mocked, never()).completeAllTasks(any(ListMetadata.class));
    }

    // Tap a todo list item to launch its corresponding TodoListActivity
    public void testTapItem() {
        Intents.init();
        addInitialData();

        pause();

        // DO UI INTERACTION
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));

        pause();

        // Confirm that the TodoListActivity was started!
        intended(hasComponent(TodoListActivity.class.getName()));
        Intents.release();
    }
}
