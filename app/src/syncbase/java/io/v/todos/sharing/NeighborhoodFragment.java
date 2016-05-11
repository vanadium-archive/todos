package io.v.todos.sharing;


import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import io.v.todos.R;

/**
 * A fragment encapsulating menu options and functionality related to list sharing.
 */
public class NeighborhoodFragment extends Fragment {
    public static final String FRAGMENT_TAG = NeighborhoodFragment.class.getSimpleName();

    private static final String PREF_ADVERTISE_NEIGHBORHOOD = "advertise neighborhood";

    private SharedPreferences mPrefs;

    private boolean isAdvertising() {
        return mPrefs.getBoolean(PREF_ADVERTISE_NEIGHBORHOOD, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.sharing, menu);
        setAdvertiseNeighborhoodChecked(menu.findItem(R.id.advertise_neighborhood), isAdvertising());
    }

    private void setAdvertiseNeighborhoodChecked(MenuItem menuItem, boolean value) {
        menuItem.setChecked(value);
        menuItem.setIcon(value ? R.drawable.ic_advertise_neighborhood_on_white_24dp :
                R.drawable.ic_advertise_neighborhood_off_white_24dp);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.advertise_neighborhood) {
            boolean advertiseNeighborhood = !item.isChecked();
            mPrefs.edit()
                    .putBoolean(PREF_ADVERTISE_NEIGHBORHOOD, advertiseNeighborhood)
                    .apply();
            setAdvertiseNeighborhoodChecked(item, advertiseNeighborhood);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
