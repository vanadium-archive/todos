// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.test.ActivityInstrumentationTestCase2;

import io.v.todos.model.ListSpec;
import io.v.todos.model.Task;
import io.v.todos.model.TaskSpec;
import io.v.todos.persistence.TodoListListener;
import io.v.todos.persistence.TodoListPersistence;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static io.v.todos.TestUtil.*;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Contains UI tests for the TodoListActivity that isolate the TodoListPersistence's behavior.
 * <p/>
 * Note: These tests will fail if the device's screen is off.
 * TODO(alexfandrianto): There do seem to be ways to force the screen to turn on though.
 * <p/>
 * Most sleeps are present in order to make the tests easier to follow when running.
 */
public class TodoListActivityTest extends ActivityInstrumentationTestCase2<TodoListActivity> {
    private TodoListActivity mActivity;

    public TodoListActivityTest() {
        super(TodoListActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Intent i = new Intent();
        i.putExtra(MainActivity.INTENT_SNACKOO_KEY, TEST_LIST_KEY);

        // Certain methods must be called before getActivity. Surprise!
        setActivityIntent(i);
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

    private TodoListPersistence mockPersistence() {
        TodoListPersistence mocked = mock(TodoListPersistence.class);
        mActivity.setPersistence(mocked);
        return mocked;
    }

    // Helper to verify that the mock was never actually called.
    private void verifyMockPersistence(TodoListPersistence mocked) {
        verifyZeroInteractions(mocked);
    }

    // Helper to verify the number of calls that occurred on the mock.
    private void verifyMockPersistence(TodoListPersistence mocked, int updateTodoList,
            int deleteTodoList, int completeTodoList, int addTask, int updateTask, int deleteTask,
            int setShowDone) {
        verify(mocked, times(updateTodoList)).updateTodoList(any(ListSpec.class));
        verify(mocked, times(deleteTodoList)).deleteTodoList();
        verify(mocked, times(completeTodoList)).completeTodoList();
        verify(mocked, times(addTask)).addTask(any(TaskSpec.class));
        verify(mocked, times(updateTask)).updateTask(any(Task.class));
        verify(mocked, times(deleteTask)).deleteTask(anyString());
        verify(mocked, times(setShowDone)).setShowDone(anyBoolean());
    }

    // Press the fab to attempt to add an item.
    public void testAttemptAddItem() {
        TodoListPersistence mocked = mockPersistence();
        AlertDialog dialog = beginAddItem(mActivity);

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();

        pause();

        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked, 0, 0, 0, 1, 0, 0, 0);
    }

    // Press the fab but don't actually add the item.
    public void testAttemptAddItemFail() {
        TodoListPersistence mocked = mockPersistence();
        AlertDialog dialog = beginAddItem(mActivity);

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).callOnClick();

        pause();

        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked);
    }

    // Press the fab but don't actually add the item.
    public void testAttemptAddItemCancel() {
        TodoListPersistence mocked = mockPersistence();
        AlertDialog dialog = beginAddItem(mActivity);

        dialog.dismiss();

        pause();

        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked);
    }

    // Add some default items so that we can interact with them with swipes.
    private void addInitialData() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                TodoListListener listener = mActivity.createTodoListListener();

                Task data = new Task(KEY1, NAME1, 0, false); // not done item
                listener.onItemAdd(data);

                Task data2 = new Task(KEY2, NAME2, 1, true); // a done item
                listener.onItemAdd(data2);
            }
        });
    }

    // This should set the title in the toolbar of the activity.
    public void testDirectlyUpdateListSpec() {
        final TodoListListener listener = mActivity.createTodoListListener();

        // Set the title of this screen.
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                ListSpec data = new ListSpec(TEST_LIST_NAME, 0);
                listener.onUpdate(data);
            }
        });

        pause();

        // CHECK THAT THE TITLE IS CORRECT!
        onView(withId(R.id.toolbar)).check(matches(hasDescendant(withText(TEST_LIST_NAME))));
    }

    // This should kick us out of the activity.
    public void testDirectlyDeleteListSpec() {
        final TodoListListener listener = mActivity.createTodoListListener();

        // Call onDelete to finish this activity.
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onDelete();
            }
        });

        pause();

        // CHECK THAT WE GOT KICKED OUT OF THE ACTIVITY!
        assertTrue(mActivity.isFinishing() || mActivity.isDestroyed());
    }

    // This should toggle the recycler view's children.
    public void testDirectlyToggleShowDone() {
        addInitialData();

        pause();

        final TodoListListener listener = mActivity.createTodoListListener();
        RecyclerView recycler = (RecyclerView) mActivity.findViewById(R.id.recycler);

        // Check that the adapter thinks that there are 2 items.
        assertEquals(recycler.getAdapter().getItemCount(), 2);

        // Set the show done status to false.
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onUpdateShowDone(false);
            }
        });

        pause();

        // Check that the adapter only thinks there is 1 item now.
        assertEquals(recycler.getAdapter().getItemCount(), 1);

        // Set the show done status to true.
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onUpdateShowDone(true);
            }
        });

        pause();

        // Check that the adapter thinks there are 2 items again.
        assertEquals(recycler.getAdapter().getItemCount(), 2);
    }

    // Call the listener directly to add an item.
    public void testDirectlyAddItem() {
        final TodoListListener listener = mActivity.createTodoListListener();

        RecyclerView recycler = (RecyclerView) mActivity.findViewById(R.id.recycler);
        pause();

        final Task data = new Task(KEY1, NAME1, 0, false);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onItemAdd(data);
            }
        });

        pause();
        assertEquals(recycler.getAdapter().getItemCount(), 1);

        final Task data2 = new Task(KEY2, NAME2, 1, true);
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

        final TodoListListener listener = mActivity.createTodoListListener();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                listener.onItemUpdate(new Task(KEY1, NAME3, 0, false));
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

        final TodoListListener listener = mActivity.createTodoListListener();
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

    // Swipe a task item to the right to toggle its done marking.
    public void testAttemptCompleteAllTasks() {
        TodoListPersistence mocked = mockPersistence();
        addInitialData();

        pause();

        // DO UI INTERACTION
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1,
                swipeRight()));

        pause();

        verifyMockPersistence(mocked, 0, 0, 0, 0, 1, 0, 0);
    }

    // Swipe a task item to the left to attempt to delete it.
    public void testAttemptDeleteItem() {
        TodoListPersistence mocked = mockPersistence();
        addInitialData();

        pause();

        // DO UI INTERACTION
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1,
                swipeLeft()));

        pause();

        verifyMockPersistence(mocked, 0, 0, 0, 0, 0, 1, 0);
    }

    // Tap a todo list item to enter its edit dialog. Tests dismiss, cancel, save, and delete.
    public void testTapItem() {
        TodoListPersistence mocked = mockPersistence();
        addInitialData();
        AlertDialog dialog;

        pause();

        // 1. DO UI INTERACTION (AND THEN DISMISS)
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click
                ()));

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.dismiss();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked);

        // 2. DO UI INTERACTION (AND THEN CANCEL)
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click
                ()));

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).callOnClick();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked);

        // 3. DO UI INTERACTION (AND THEN SAVE!)
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click
                ()));

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked, 0, 0, 0, 0, 1, 0, 0);

        // 4. DO UI INTERACTION (AND THEN DELETE!)
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.actionOnItemAtPosition(1, click
                ()));

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).callOnClick();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked, 0, 0, 0, 0, 1, 1, 0);
    }

    private void tapMenuItemInMenu(int stringId) {
        openActionBarOverflowOrOptionsMenu(mActivity);

        pause();

        // Use the string id if it was in overflow/options. These don't have ids.
        onView(withText(stringId)).perform(click());
    }

    public void testTapMenuEdit() {
        TodoListPersistence mocked = mockPersistence();
        AlertDialog dialog;

        // SETUP: We must have a title (or TaskSpec), otherwise this edit dialog doesn't make sense.
        final TodoListListener listener = mActivity.createTodoListListener();

        // Set the title of this screen.
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                ListSpec data = new ListSpec(TEST_LIST_NAME, 0);
                listener.onUpdate(data);
            }
        });

        pause();

        // 1. DISMISS THE DIALOG
        tapMenuItemInMenu(R.string.action_edit);

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.dismiss();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked);

        // 2. CANCEL THE DIALOG
        tapMenuItemInMenu(R.string.action_edit);

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).callOnClick();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked);

        // 3. PRESS SAVE
        tapMenuItemInMenu(R.string.action_edit);

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked, 1, 0, 0, 0, 0, 0, 0);

        // 4. PRESS DELETE
        tapMenuItemInMenu(R.string.action_edit);

        pause();

        dialog = handleEditDialog(mActivity);
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).callOnClick();

        pause();
        assertFalse(dialog.isShowing());

        verifyMockPersistence(mocked, 1, 1, 0, 0, 0, 0, 0);
    }

    public void testTapMenuMarkAllDone() {
        TodoListPersistence mocked = mockPersistence();

        tapMenuItemInMenu(R.string.action_all_done);

        pause();

        verifyMockPersistence(mocked, 0, 0, 1, 0, 0, 0, 0);
    }

    public void testTapMenuShowDone() {
        TodoListPersistence mocked = mockPersistence();

        tapMenuItemInMenu(R.string.show_done);

        pause();

        verifyMockPersistence(mocked, 0, 0, 0, 0, 0, 0, 1);
    }
}
