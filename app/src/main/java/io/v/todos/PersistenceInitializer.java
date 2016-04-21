// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.v.todos;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Throwables;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.v.todos.persistence.Persistence;

/**
 * Persistence initialization may or may not block. For non-blocking persistence initialization,
 * this class simply calls {@link #initPersistence()} and
 * {@link #onSuccess(Persistence)}/{@link #onFailure(Exception)} in a single thread. For blocking
 * persistence initialization, it shows a {@link ProgressDialog} while initializing persistence on
 * a worker thread. This obscures the main UI in a friendly way until persistence is ready.
 */
public abstract class PersistenceInitializer<T extends Persistence> {
    private static final String TAG = PersistenceInitializer.class.getSimpleName();
    // TODO(rosswang): https://github.com/vanadium/issues/issues/1309
    private static final Executor sInitExecutor = Executors.newCachedThreadPool();

    protected final Activity mActivity;

    public PersistenceInitializer(Activity activity) {
        mActivity = activity;
    }

    protected abstract T initPersistence() throws Exception;

    protected abstract void onSuccess(T persistence);

    protected void onFailure(Exception e) {
        Toast.makeText(mActivity, R.string.err_init, Toast.LENGTH_LONG).show();
        Log.e(TAG, Throwables.getStackTraceAsString(e));
        mActivity.finish();
    }

    public void execute(boolean mayBlock) {
        if (mayBlock) {
            final ProgressDialog progress = new ProgressDialog(mActivity);
            progress.setMessage(mActivity.getString(R.string.init_persistence));
            progress.setCancelable(false);
            progress.show();

            new AsyncTask<Void, Void, Object>() {
                @Override
                protected Object doInBackground(Void... params) {
                    try {
                        return initPersistence();
                    } catch (Exception e) {
                        return e;
                    }
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void onPostExecute(Object o) {
                    if (o instanceof Exception) {
                        onFailure((Exception) o);
                    } else {
                        onSuccess((T) o);
                    }
                    progress.dismiss();
                }
            }.executeOnExecutor(sInitExecutor);
        } else {
            T persistence;
            try {
                persistence = initPersistence();
            } catch (Exception e) {
                onFailure(e);
                return;
            }
            onSuccess(persistence);
        }
    }
}
