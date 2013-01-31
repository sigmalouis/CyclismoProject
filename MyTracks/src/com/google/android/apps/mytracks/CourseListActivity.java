/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.mytracks;

import com.google.android.apps.mytracks.content.CourseTracksColumns;
import com.google.android.apps.mytracks.content.MyTracksCourseProviderUtils;
import com.google.android.apps.mytracks.content.Track;
import com.google.android.apps.mytracks.content.TrackDataListener;
import com.google.android.apps.mytracks.content.Waypoint;
import com.google.android.apps.mytracks.fragments.DeleteAllTrackDialogFragment;
import com.google.android.apps.mytracks.fragments.DeleteOneTrackDialogFragment;
import com.google.android.apps.mytracks.fragments.DeleteOneTrackDialogFragment.DeleteOneTrackCaller;
import com.google.android.apps.mytracks.io.file.GpxImporter;
import com.google.android.apps.mytracks.services.TrackRecordingServiceConnection;
import com.google.android.apps.mytracks.util.ApiAdapterFactory;
import com.google.android.apps.mytracks.util.IntentUtils;
import com.google.android.apps.mytracks.util.ListItemUtils;
import com.google.android.apps.mytracks.util.PreferencesUtils;
import com.google.android.apps.mytracks.util.StringUtils;
import com.google.android.apps.mytracks.util.TrackIconUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.maps.mytracks.R;
import com.ipaulpro.afilechooser.utils.FileUtils;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.ResourceCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

/**
 * An activity displaying a list of tracks.
 * 
 * @author Leif Hendrik Wilden
 */
public class CourseListActivity extends FragmentActivity implements DeleteOneTrackCaller {
  private static final String TAG = CourseListActivity.class.getSimpleName();
  private static final int GOOGLE_PLAY_SERVICES_REQUEST_CODE = 0;
  private static final String[] PROJECTION = new String[] { CourseTracksColumns._ID, CourseTracksColumns.NAME,
      CourseTracksColumns.DESCRIPTION, CourseTracksColumns.CATEGORY, CourseTracksColumns.STARTTIME,
      CourseTracksColumns.TOTALDISTANCE, CourseTracksColumns.TOTALTIME, CourseTracksColumns.ICON };


  /*
   * Note that sharedPreferenceChangeListenr cannot be an anonymous inner class.
   * Anonymous inner class will get garbage collected.
   */
  private final OnSharedPreferenceChangeListener
      sharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
          @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
          if (key == null || key.equals(
              PreferencesUtils.getKey(CourseListActivity.this, R.string.metric_units_key))) {
            metricUnits = PreferencesUtils.getBoolean(CourseListActivity.this,
                R.string.metric_units_key, PreferencesUtils.METRIC_UNITS_DEFAULT);
          }
          if (key != null) {
            runOnUiThread(new Runnable() {
                @Override
              public void run() {
                updateMenuItems();
                resourceCursorAdapter.notifyDataSetChanged();
              }
            });
          }
        }
      };

  // Callback when an item is selected in the contextual action mode
  private final ContextualActionModeCallback
      contextualActionModeCallback = new ContextualActionModeCallback() {
          @Override
        public boolean onClick(int itemId, int position, long id) {
          return handleContextItem(itemId, id);
        }
      };


  private final TrackDataListener trackDataListener = new TrackDataListener() {
      @Override
    public void onTrackUpdated(Track track) {
      // Ignore
    }

      @Override
    public void onSelectedTrackChanged(Track track) {
      // Ignore
    }

      @Override
    public void onSegmentSplit(Location location) {
      // Ignore
    }

      @Override
    public void onSampledOutTrackPoint(Location location) {
      // Ignore
    }

      @Override
    public void onSampledInTrackPoint(Location location) {
      // Ignore
    }

      @Override
    public boolean onReportSpeedChanged(boolean reportSpeed) {
      return false;
    }

      @Override
    public void onNewWaypointsDone() {
      // Ignore
    }

      @Override
    public void onNewWaypoint(Waypoint waypoint) {
      // Ignore
    }

      @Override
    public void onNewTrackPointsDone() {
      // Ignore
    }

      @Override
    public boolean onMinRecordingDistanceChanged(int minRecordingDistance) {
      return false;
    }

      @Override
    public boolean onMetricUnitsChanged(boolean isMetricUnits) {
      return false;
    }

      @Override
    public void onLocationStateChanged(LocationState locationState) {
      // Ignore
    }

      @Override
    public void onLocationChanged(Location location) {
      // Ignore
    }

      @Override
    public void onHeadingChanged(double heading) {
      // Ignore
    }

      @Override
    public void clearWaypoints() {
      // Ignore
    }

      @Override
    public void clearTrackPoints() {
      // Ignore
    }
  };

  // The following are set in onCreate
  private SharedPreferences sharedPreferences;
  //private TrackRecordingServiceConnection trackRecordingServiceConnection;
  //private TrackController trackController;
  private ListView listView;
  private ResourceCursorAdapter resourceCursorAdapter;

  // Preferences
  private boolean metricUnits = PreferencesUtils.METRIC_UNITS_DEFAULT;
  
  private Button importButton;
  private Button cancelButton;
  
  private static final int REQUEST_CODE = 0x1234;

  //private MenuItem deleteAllMenuItem;


  private void updateTrackIdSharedPreference(long id) {
    Track track = new MyTracksCourseProviderUtils(CourseListActivity.this.getContentResolver()).getTrack(id);
    if (track == null) {
      id = -1;
    }
    PreferencesUtils.setLong(CourseListActivity.this, R.string.course_track_id, id);
  }
  
  /**
   * This also syncs up the shared preference
   * @return
   */
  private Long getTrackIdSharedPreference() {
    Long id = PreferencesUtils.getLong(this, R.string.course_track_id);
    Track track = new MyTracksCourseProviderUtils(CourseListActivity.this.getContentResolver()).getTrack(id);
    if (track == null) {
      id = -1L;
    }
    return id;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.course_list);

    sharedPreferences = getSharedPreferences(Constants.SETTINGS_NAME, Context.MODE_PRIVATE);

    listView = (ListView) findViewById(R.id.course_list);
    listView.setEmptyView(findViewById(R.id.course_list_empty_view));
    listView.setOnItemClickListener(new OnItemClickListener() {
        @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          updateTrackIdSharedPreference(id);
          //Editor editor = sharedPreferences.edit();
          //editor.putLong(PreferencesUtils.getKey(CourseListActivity.this,R.string.course_track_id), id);
          //editor.apply();
          doFinish();
      }

    });
    resourceCursorAdapter = new ResourceCursorAdapter(this, R.layout.list_item, null, 0) {
        @Override
      public void bindView(View view, Context context, Cursor cursor) {
        int idIndex = cursor.getColumnIndex(CourseTracksColumns._ID);
        int iconIndex = cursor.getColumnIndex(CourseTracksColumns.ICON);
        int nameIndex = cursor.getColumnIndex(CourseTracksColumns.NAME);
        int categoryIndex = cursor.getColumnIndex(CourseTracksColumns.CATEGORY);
        int totalTimeIndex = cursor.getColumnIndexOrThrow(CourseTracksColumns.TOTALTIME);
        int totalDistanceIndex = cursor.getColumnIndexOrThrow(CourseTracksColumns.TOTALDISTANCE);
        int startTimeIndex = cursor.getColumnIndexOrThrow(CourseTracksColumns.STARTTIME);
        int descriptionIndex = cursor.getColumnIndex(CourseTracksColumns.DESCRIPTION);

        int iconId = TrackIconUtils.getIconDrawable(cursor.getString(iconIndex));
        String name = cursor.getString(nameIndex);
        String totalTime = StringUtils.formatElapsedTime(cursor.getLong(totalTimeIndex));
        String totalDistance = StringUtils.formatDistance(
            CourseListActivity.this, cursor.getDouble(totalDistanceIndex), metricUnits);
        long startTime = cursor.getLong(startTimeIndex);
        String startTimeDisplay = StringUtils.formatDateTime(context, startTime).equals(name) ? null
            : StringUtils.formatRelativeDateTime(context, startTime);
        
        Log.d(TAG,"resourceCursorAdapter.bindview : ListItemUtils.setListItem");
        ListItemUtils.setListItem(CourseListActivity.this, view, false, false,
            iconId, R.string.icon_track, name, cursor.getString(categoryIndex), totalTime,
            totalDistance, startTimeDisplay, cursor.getString(descriptionIndex));
        Log.d(TAG,"resourceCursorAdapter.bindview : ListItemUtils.setListItem finsihed");
      }
    };
    listView.setAdapter(resourceCursorAdapter);
    ApiAdapterFactory.getApiAdapter()
        .configureListViewContextualMenu(this, listView, contextualActionModeCallback);

    getSupportLoaderManager().initLoader(0, null, new LoaderCallbacks<Cursor>() {
        @Override
      public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        Log.d(TAG,"onCreateLoader");
        CursorLoader rtn = new CursorLoader(CourseListActivity.this, CourseTracksColumns.CONTENT_URI, PROJECTION, null,
            null, CourseTracksColumns._ID + " DESC");
        Log.d(TAG,"onCreateLoader returning");
        return rtn;
      }

        @Override
      public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Log.d(TAG,"onLoadFinished");
        resourceCursorAdapter.swapCursor(cursor);
        Log.d(TAG,"onLoadFinished returning");
      }

        @Override
      public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(TAG,"onLoaderReset");
        resourceCursorAdapter.swapCursor(null);
        Log.d(TAG,"onLoaderReset returning");
      }
    });
    
    importButton = (Button) this.findViewById(R.id.course_import_button);
    importButton.setOnClickListener(new View.OnClickListener() {
      
      @Override
      public void onClick(View v) {
        showChooser();
        
      }
    });
    
    cancelButton = (Button) this.findViewById(R.id.course_cancel_button);
    
    cancelButton.setOnClickListener(new View.OnClickListener() {
      
      @Override
      public void onClick(View v) {
        
        cancel();     
      }
    });

    showStartupDialogs();
  }
  
  private void cancel() {
    Intent resultData = new Intent();
    resultData.putExtra(getString(R.string.course_track_id), getTrackIdSharedPreference());
    setResult(Activity.RESULT_CANCELED, resultData);
    finish();
  }
  
  private void doFinish() {
    Intent resultData = new Intent();
    resultData.putExtra(getString(R.string.course_track_id), getTrackIdSharedPreference());
    setResult(Activity.RESULT_OK, resultData);
    finish();
  }
  
  
  private void showChooser() {
    // Use the GET_CONTENT intent from the utility class
    Intent target = FileUtils.createGetContentIntent();
    // Create the chooser Intent
    Intent intent = Intent.createChooser(
            target, getString(R.string.course_file_chooser_title));
    try {
        startActivityForResult(intent, REQUEST_CODE);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(CourseListActivity.this, 
          "SecurityException: unable to load file manager", Toast.LENGTH_LONG).show();
      Log.e(TAG,e.toString());
    } 
}


  @Override
  protected void onStart() {
    super.onStart();

    // Register shared preferences listener
    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

    // Update shared preferences
    sharedPreferenceChangeListener.onSharedPreferenceChanged(null, null);



  }

  @Override
  protected void onResume() {
    super.onResume();


    // Update UI
    updateMenuItems();
    resourceCursorAdapter.notifyDataSetChanged();
  }
  
  @Override
  protected void onPause() {
    super.onPause();

    // Update track data hub
    //trackDataHub.unregisterTrackDataListener(trackDataListener);

    // Update UI
  }

  @Override
  protected void onStop() {
    super.onStop();

    // Unregister shared preferences listener
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);


    //trackDataHub.stop();

  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == GOOGLE_PLAY_SERVICES_REQUEST_CODE) {
      checkGooglePlayServices();
    } else {
      
      switch (requestCode) {
        case REQUEST_CODE:  
            // If the file selection was successful
            if (resultCode == RESULT_OK) {      
                if (data != null) {
                    // Get the URI of the selected file
                    final Uri uri = data.getData();

                    try {
                        // Create a file instance from the URI
                        final File file = FileUtils.getFile(uri);
                        InputStream fileStream = new FileInputStream(file);
                        Toast.makeText(CourseListActivity.this, 
                                "importing: "+file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        importGpx(fileStream);
                    } catch (IOException e) {
                        Log.e(TAG, "File select error", e);
                    }
                }
            } 
            break;
        }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
  
  private void importGpx(InputStream fileStream) {
    try {
    GpxImporter.importGPXFile(fileStream,new MyTracksCourseProviderUtils(this.getContentResolver()),PreferencesUtils.MIN_RECORDING_DISTANCE_DEFAULT);
    } catch (ParserConfigurationException e) {
      final String msg = "error parsing gpx file";
      Log.e(TAG,msg);
      Toast.makeText(CourseListActivity.this, 
          msg, Toast.LENGTH_LONG).show();
      
    } catch (SAXException e) {
      final String msg = "error parsing gpx file";
      Log.e(TAG,msg);
      Toast.makeText(CourseListActivity.this, 
          msg, Toast.LENGTH_LONG).show();
    } catch (IOException e) {
      final String msg = "IOException whilst parsing gpx file";
      Log.e(TAG,msg);
      Toast.makeText(CourseListActivity.this, 
          msg, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.course_list, menu);

    //deleteAllMenuItem = menu.findItem(R.id.course_list_delete_all);

    updateMenuItems();
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.course_list_delete_all:
        new DeleteAllTrackDialogFragment(new MyTracksCourseProviderUtils(this.getContentResolver())).show(
            getSupportFragmentManager(), DeleteAllTrackDialogFragment.DELETE_ALL_TRACK_DIALOG_TAG);
        return true;
    }
    return true;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    getMenuInflater().inflate(R.menu.list_context_menu, menu);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if (handleContextItem(item.getItemId(), ((AdapterContextMenuInfo) item.getMenuInfo()).id)) {
      return true;
    }
    return super.onContextItemSelected(item);
  }


  /**
   * Shows start up dialogs.
   */
  public void showStartupDialogs() {

      findViewById(R.id.course_list_empty_view).setVisibility(View.VISIBLE);
      
      checkGooglePlayServices();
   
  }

  private void checkGooglePlayServices() {
    int code = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
    if (code != ConnectionResult.SUCCESS) {
      Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
          code, this, GOOGLE_PLAY_SERVICES_REQUEST_CODE, new DialogInterface.OnCancelListener() {
  
              @Override
            public void onCancel(DialogInterface dialogInterface) {
              doFinish();
            }
          });
      if (dialog != null) {
        dialog.show();
      }
    }
  }

  /**
   * Updates the menu items.
   * 
   * @param isRecording true if recording
   */
  private void updateMenuItems() {

   //if (deleteAllMenuItem != null) {
   //   deleteAllMenuItem.setVisible(false);
    //}
  }

  /**
   * Handles a context item selection.
   * 
   * @param itemId the menu item id
   * @param trackId the track id
   * @return true if handled.
   */
  private boolean handleContextItem(int itemId, long trackId) {
    Intent intent;
    switch (itemId) {
      case R.id.list_context_menu_show_on_map:
        intent = IntentUtils.newIntent(this, TrackDetailActivity.class)
            .putExtra(TrackDetailActivity.EXTRA_TRACK_ID, trackId)
            .putExtra(TrackDetailActivity.EXTRA_USE_COURSE_PROVIDER, true);
        startActivity(intent);
        return true;
      case R.id.list_context_menu_edit:
        intent = IntentUtils.newIntent(this, TrackEditActivity.class)
            .putExtra(TrackEditActivity.EXTRA_TRACK_ID, trackId)
            .putExtra(TrackEditActivity.EXTRA_USE_COURSE_PROVIDER, true);
        startActivity(intent);
        return true;
      case R.id.list_context_menu_delete:
        DeleteOneTrackDialogFragment.newInstance(trackId, true).show(
            getSupportFragmentManager(), DeleteOneTrackDialogFragment.DELETE_ONE_TRACK_DIALOG_TAG);
        return true;
      default:
        return false;
    }
  }

  @Override
  public TrackRecordingServiceConnection getTrackRecordingServiceConnection() {
    return null;
  }




  
}
