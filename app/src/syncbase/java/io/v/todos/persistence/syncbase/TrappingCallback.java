// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos.persistence.syncbase;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import io.v.android.error.ErrorReporter;
import io.v.v23.verror.CanceledException;
import io.v.v23.verror.ExistException;

/**
 * A {@link FutureCallback} that reports persistence errors by toasting a short message to the
 * user and logging the exception trace and the call stack from where the future was invoked.
 *
 * TODO(rosswang): Factor into V23.
 */
public class TrappingCallback<T> implements FutureCallback<T> {
    private static final int FIRST_SIGNIFICANT_STACK_ELEMENT = 3;
    private final @StringRes int mFailureMessage;
    private final String mLogTag;
    private final ErrorReporter mErrorReporter;
    private final StackTraceElement[] mCaller;

    public TrappingCallback(@StringRes int failureMessage, String logTag,
                            ErrorReporter errorReporter) {
        mFailureMessage = failureMessage;
        mLogTag = logTag;
        mErrorReporter = errorReporter;
        mCaller = Thread.currentThread().getStackTrace();
    }

    @Override
    public void onSuccess(@Nullable T result) {
    }

    @Override
    public void onFailure(@NonNull Throwable t) {
        if (!(t instanceof CanceledException || t instanceof ExistException)) {
            mErrorReporter.onError(mFailureMessage, t);

            StringBuilder traceBuilder = new StringBuilder(t.getMessage())
                    .append("\n invoked at ").append(mCaller[FIRST_SIGNIFICANT_STACK_ELEMENT]);
            for (int i = FIRST_SIGNIFICANT_STACK_ELEMENT + 1; i < mCaller.length; i++) {
                traceBuilder.append("\n\tat ").append(mCaller[i]);
            }
            Log.e(mLogTag, traceBuilder.toString());
        }
    }
}
