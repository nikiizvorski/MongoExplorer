package com.innodroid.mongobrowser.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.innodroid.mongobrowser.Constants;
import com.innodroid.mongobrowser.Events;
import com.innodroid.mongobrowser.R;
import com.innodroid.mongobrowser.data.MongoBrowserProvider;
import com.innodroid.mongobrowser.data.MongoCollectionAdapter;
import com.innodroid.mongobrowser.data.MongoConnectionAdapter;

import butterknife.Bind;
import butterknife.OnItemClick;
import de.greenrobot.event.EventBus;

public class ConnectionListFragment extends BaseFragment implements LoaderCallbacks<Cursor> {
    @Bind(android.R.id.list) ListView mList;

    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    private MongoConnectionAdapter mAdapter;
    private int mActivatedPosition = ListView.INVALID_POSITION;
    private long mSelectAfterLoad;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
            mActivatedPosition = savedInstanceState.getInt(STATE_ACTIVATED_POSITION);
        }

		setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(R.layout.fragment_generic_list, inflater, container, savedInstanceState);

        if (mAdapter == null) {
            mAdapter = new MongoConnectionAdapter(getActivity(), null, true);
            getLoaderManager().initLoader(0, null, this);
        }

        mList.setAdapter(mAdapter);

        mList.setChoiceMode(getArguments().getBoolean(Constants.ARG_ACTIVATE_ON_CLICK)
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE);

        if (mActivatedPosition != ListView.INVALID_POSITION) {
            setActivatedPosition(mActivatedPosition);
        }

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    	inflater.inflate(R.menu.connection_list_menu, menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connection_list_add:
                Events.postAddConnection();
                return true;
            case R.id.menu_connection_list_configure:
            	Intent intent = new Intent(getActivity(), PreferencesActivity.class);
            	startActivity(intent);
                return true;
        }

    	return super.onOptionsItemSelected(item);
    }

    @OnItemClick(android.R.id.list)
    public void onItemClick(int position) {
        setActivatedPosition(position);

        Events.postConnectionSelected(mAdapter.getItemId(position));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    public void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            mList.setItemChecked(mActivatedPosition, false);
        } else {
            mList.setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }
    
	public Loader<Cursor> onCreateLoader(int id, Bundle params) {
	    return new CursorLoader(getActivity(), MongoBrowserProvider.CONNECTION_URI, null, null, null, MongoBrowserProvider.NAME_CONNECTION_NAME);
	}

	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		mAdapter.swapCursor(cursor);
		
		if (mSelectAfterLoad > 0)
			selectItem(cursor, mSelectAfterLoad);
		else
			setActivatedPosition(mActivatedPosition);
		
		mSelectAfterLoad = 0;
	}

	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}

	private void selectItem(Cursor cursor, long id) {
		int pos = 0;
		int original = cursor.getPosition();
		if (!cursor.moveToFirst())
			return;

		do {
			if (cursor.getLong(MongoBrowserProvider.INDEX_CONNECTION_ID) == id)
				break;
			pos++;
		} while (cursor.moveToNext());
		
		cursor.moveToPosition(original);
		
		setActivatedPosition(pos);
	}

	public void reloadAndSelect(long id) {
		mSelectAfterLoad = id;
		getLoaderManager().initLoader(0, null, this);		
	}

	public int getConnectionCount() {
		return mAdapter.getCount();
	}
}
