package com.innodroid.mongobrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.innodroid.mongo.MongoHelper;
import com.innodroid.mongobrowser.data.MongoBrowserProvider;
import com.innodroid.mongobrowser.data.MongoBrowserProviderHelper;
import com.innodroid.mongobrowser.util.UiUtils;
import com.innodroid.mongobrowser.util.UiUtils.AlertDialogCallbacks;

public class ConnectionDetailFragment extends Fragment implements LoaderCallbacks<Cursor>, EditConnectionDialogFragment.Callbacks {

	private long mConnectionID;
	private TextView mTitle;
	private TextView mServer;
	private TextView mPort;
	private TextView mDB;
	private TextView mUser;
	private TextView mLastConnect;
	private Callbacks mCallbacks;

    public interface Callbacks {
    	public void onConnectionDeleted();
        public void onConnected();
    }

    public ConnectionDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setHasOptionsMenu(true);
    	
    	mConnectionID = getArguments().getLong(Constants.ARG_CONNECTION_ID);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	View view = inflater.inflate(R.layout.fragment_connection_detail, container, false);
    	
        mTitle = (TextView) view.findViewById(R.id.connection_detail_title);
        mServer = (TextView) view.findViewById(R.id.connection_detail_server);
        mPort = (TextView) view.findViewById(R.id.connection_detail_port);
        mDB = (TextView) view.findViewById(R.id.connection_detail_db);
        mUser = (TextView) view.findViewById(R.id.connection_detail_user);
        mLastConnect = (TextView) view.findViewById(R.id.connection_detail_last_connect);
        
        Button button = (Button) view.findViewById(R.id.connection_detail_connect);
        button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				new ConnectTask().execute();
			}        	
        });

        return view;
    }
    
    @Override
    public void onResume() {
    	super.onResume();
        
        getLoaderManager().initLoader(0, getArguments(), this);
    }
    
    @Override
    public void onAttach(Activity activity) {
    	super.onAttach(activity);
    	
    	mCallbacks = (Callbacks)activity;
    }
    
    @Override
    public void onDetach() {
    	super.onDetach();
    	
    	mCallbacks = null;
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    	inflater.inflate(R.menu.connection_detail_menu, menu);
    }    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case R.id.connection_detail_menu_edit:
    			editConnection();
    			return true;
    		case R.id.connection_detail_menu_delete:
    			deleteConnection();
    			return true;
        }

    	return super.onOptionsItemSelected(item);
    }

	public Loader<Cursor> onCreateLoader(int arg0, Bundle args) {
		Uri uri = ContentUris.withAppendedId(MongoBrowserProvider.CONNECTION_URI, mConnectionID);
	    return new CursorLoader(getActivity(), uri, null, null, null, null);
	}

	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		Resources res = getResources();
		
		if (!cursor.moveToFirst())
			return;
		
		mTitle.setText(cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_NAME));
		mServer.setText(res.getString(R.string.server) + " : " + cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_NAME));
		mPort.setText(res.getString(R.string.port) + " : " + cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_PORT));
		mDB.setText(res.getString(R.string.database) + " : " + cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_DB));
		mUser.setText(res.getString(R.string.user) + " : " + cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_USER));
		
		long lastConnect = cursor.getLong(MongoBrowserProvider.INDEX_CONNECTION_LAST_CONNECT);
		if (lastConnect == 0)
			mLastConnect.setText(getResources().getString(R.string.never_connected));
		else
			mLastConnect.setText(String.format(getResources().getString(R.string.last_connected), DateUtils.getRelativeTimeSpanString(lastConnect)));
	}

	public void onLoaderReset(Loader<Cursor> arg0) {
	}
	
    private void editConnection() {
        DialogFragment fragment = EditConnectionDialogFragment.create(mConnectionID, this);
        fragment.show(getActivity().getSupportFragmentManager(), null);
    }

    private void deleteConnection() {
    	UiUtils.confirm(getActivity(), R.string.confirm_delete_connection, new AlertDialogCallbacks() {
			@Override
			public boolean onOK() {
            	new DeleteConnectionTask().execute();
				return true;
			}
    	});
    }
    
	@Override
	public void onConnectionEdited(long id) {
	}

	private class ConnectTask extends AsyncTask<Void, Void, Boolean>{
		private String mException;
		private ProgressDialog mDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			mDialog = ProgressDialog.show(getActivity(), null, "Connecting...", true, false);		
		}
		
		@Override
		protected Boolean doInBackground(Void... arg0) {
			try {
				Uri uri = ContentUris.withAppendedId(MongoBrowserProvider.CONNECTION_URI, mConnectionID);
				Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
				cursor.moveToFirst();
				
		    	String server = cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_SERVER);
		    	int port = cursor.getInt(MongoBrowserProvider.INDEX_CONNECTION_PORT);
		    	String database = cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_DB);
		    	String user = cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_USER);
		    	String password = cursor.getString(MongoBrowserProvider.INDEX_CONNECTION_PASSWORD); 
				
		    	MongoHelper.connect(server, port, database, user, password);
		    	new MongoBrowserProviderHelper(getActivity().getContentResolver()).updateConnectionLastConnect(mConnectionID);
		    	
				return true;
			} catch (Exception ex) {
				mException = ex.getMessage();
				return false;
			}
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			mDialog.dismiss();
			
			if (result) {
				mCallbacks.onConnected();
			} else {
		        new AlertDialog.Builder(getActivity())
	                .setIcon(android.R.drawable.ic_menu_delete)
	                .setMessage(mException)
	                .setTitle(R.string.connect_failed)
	                .setCancelable(true)
	                .setPositiveButton(android.R.string.ok,
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog, int whichButton) {
	                        	//
	                        }
	                    }
	                )
	                .create().show();
			}
		}
	}
	
    private class DeleteConnectionTask extends AsyncTask<Void, Void, Boolean> {
    	private Exception mException;
    	
		@Override
		protected Boolean doInBackground(Void... arg0) {
			try {
				Uri uri = ContentUris.withAppendedId(MongoBrowserProvider.CONNECTION_URI, mConnectionID);
				getActivity().getContentResolver().delete(uri, null, null);
				return true;
			} catch (Exception ex) {
				mException = ex;
				return false;
			}
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (mException == null) {
				mCallbacks.onConnectionDeleted();
			} else {
				Toast.makeText(getActivity(), mException.getMessage(), Toast.LENGTH_SHORT).show();
			}
		}
    }
}
