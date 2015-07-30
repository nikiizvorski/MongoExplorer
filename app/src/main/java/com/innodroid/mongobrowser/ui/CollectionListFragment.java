package com.innodroid.mongobrowser.ui;


import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.innodroid.mongobrowser.Events;
import com.innodroid.mongobrowser.util.MongoHelper;
import com.innodroid.mongobrowser.Constants;
import com.innodroid.mongobrowser.R;
import com.innodroid.mongobrowser.data.MongoCollectionAdapter;
import com.innodroid.mongobrowser.util.SafeAsyncTask;
import com.innodroid.mongobrowser.util.UiUtils;

public class CollectionListFragment extends ListFragment {
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    private long mConnectionId;
    private MongoCollectionAdapter mAdapter;
    private int mActivatedPosition = ListView.INVALID_POSITION;

    public CollectionListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mConnectionId = getArguments().getLong(Constants.ARG_CONNECTION_ID);
		mAdapter = new MongoCollectionAdapter(getActivity());
		setListAdapter(mAdapter);
		setHasOptionsMenu(true);

		if (savedInstanceState != null)
			mActivatedPosition = savedInstanceState.getInt(STATE_ACTIVATED_POSITION);

		reloadList();
    }

	public void onEvent(Events.DocumentDeleted e) {
		reloadList();
	}

	public void reloadList() {
    	new LoadNamesTask().execute();
	}

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    	inflater.inflate(R.menu.collection_list_menu, menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_collection_list_add:
            	addCollection();
                return true;
            case R.id.menu_collection_list_change_db:
            	changeDatabase();
                return true;
        }

    	return super.onOptionsItemSelected(item);
    }
    
    private void addCollection() {
        DialogFragment fragment = CollectionEditDialogFragment.create("", true);
        fragment.setTargetFragment(this, 0);
        fragment.show(getFragmentManager(), null);
	}
    
    private void changeDatabase() {
    	new GetDatabasesTask().execute();
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getListView().setChoiceMode(getArguments().getBoolean(Constants.ARG_ACTIVATE_ON_CLICK)
				? ListView.CHOICE_MODE_SINGLE
				: ListView.CHOICE_MODE_NONE);
        
        if (mActivatedPosition != ListView.INVALID_POSITION)
            setActivatedPosition(mActivatedPosition);
    }
    
    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        
        setActivatedPosition(position);

		Events.postCollectionSelected(mConnectionId, mAdapter.getCollectionName(position));
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
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }
    
	public void onEvent(Events.CreateCollection e) {
		new AddCollectionTask().execute(e.Name);
	}

	public void onEvent(Events.CollectionRenamed e) {
		mAdapter.setItemName(mActivatedPosition, e.Name);
	}
	
	public void onEvent(Events.ChangeDatabase e) {
		new ChangeDatabaseTask().execute(e.Name);
	}

	public void onEvent(Events.CollectionDropped e) {
		mAdapter.delete(mActivatedPosition);

		if (mActivatedPosition < mAdapter.getCount())
			Events.postCollectionSelected(mConnectionId, mAdapter.getItem(mActivatedPosition).Name);
		else {
			Events.postCollectionSelected(mConnectionId, null);
			mActivatedPosition = ListView.INVALID_POSITION;
		}
	}

    private class AddCollectionTask extends SafeAsyncTask<String, Void, String> {
    	public AddCollectionTask() {
			super(getActivity());
		}

    	@Override
		protected String safeDoInBackground(String... args) throws Exception {
			MongoHelper.createCollection(args[0]);
			return args[0];
		}

		@Override
		protected void safeOnPostExecute(String result) {
			mAdapter.add(0, result);
			setActivatedPosition(0);
			Events.postCollectionSelected(mConnectionId, result);
		}

		@Override
		protected String getErrorTitle() {
			return "Failed to Add";
		}		
    }
	
    private class LoadNamesTask extends SafeAsyncTask<Void, Void, String[]> {
    	public LoadNamesTask() {
			super(getActivity());
		}

    	@Override
		protected String[] safeDoInBackground(Void... arg0) {
			boolean includeSystem = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(Constants.PrefShowSystemCollections, false);
			return MongoHelper.getCollectionNames(includeSystem);
		}

		@Override
		protected void safeOnPostExecute(String[] result) {
			mAdapter.loadItems(result);
			new LoadCountsTask().execute(result);
		}

		@Override
		protected String getErrorTitle() {
			return "Failed to Get Names";
		}		
    }

    private class LoadCountsTask extends SafeAsyncTask<String, Long, Void> {
    	public LoadCountsTask() {
			super(getActivity());
		}

    	@Override
		protected Void safeDoInBackground(String... names) {    		
			for (int i = 0; i<names.length; i++) {
				publishProgress(new Long[] { (long)i, MongoHelper.getCollectionCount(names[i])});
			}
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Long... values) {
			super.onProgressUpdate(values);
			
			int index = (int)(long)values[0];
			mAdapter.setItemCount(index, values[1]);
		}
		
		@Override
		protected void safeOnPostExecute(Void result) {
		}

		@Override
		protected String getErrorTitle() {
			return "Failed to Get Counts";
		}
    }
    
    private class GetDatabasesTask extends SafeAsyncTask<String, Void, ArrayList<String>> {
    	public GetDatabasesTask() {
			super(getActivity());
		}

    	@Override
		protected ArrayList<String> safeDoInBackground(String... args) throws Exception {
			return MongoHelper.getDatabaseNames();
		}

		@Override
		protected void safeOnPostExecute(ArrayList<String> result) {
			if (result == null || result.size() == 0) {
				UiUtils.message(getActivity(), R.string.title_no_databases, R.string.message_no_databases);
				return;
			}

	        DialogFragment fragment = ChangeDatabaseDialogFragment.create(result);
	        fragment.setTargetFragment(CollectionListFragment.this, 0);
	        fragment.show(getFragmentManager(), null);    	
		}

		@Override
		protected String getErrorTitle() {
			return "Failed to get database list";
		}		
    }
    
    private class ChangeDatabaseTask extends SafeAsyncTask<String, Void, String> {
    	public ChangeDatabaseTask() {
			super(getActivity());
		}

    	@Override
		protected String safeDoInBackground(String... args) throws Exception {
    		MongoHelper.changeDatabase(args[0]);
    		return args[0];
		}

		@Override
		protected void safeOnPostExecute(String result) {
			reloadList();
		}

		@Override
		protected String getErrorTitle() {
			return "Failed to Change DB";
		}		
    }
}