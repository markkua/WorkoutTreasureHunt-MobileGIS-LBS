package ch.ethz.mobilegis.treasurehunt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;


/**
 * Assignment 2
 * */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureEditResult;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.MapView;

/**
 * UploadFeatureActivity.java
 *
 * Author: Bingxin Ke
 * Last edited: 2021-05-06
 *
 * New component to solve Assignment 2 - task 2
 * Used to upload result to ArcGIS server.
 * Automatically close when finishes.
 * */

public class UploadFeatureActivity extends AppCompatActivity {
    public static final String TRACK_OBJ_KEY = "TrackObj";
    public static final String POINT_OBJ_KEY = "PointObj";
    public static final String GAME_RESULT_BDL_KEY = "GameResultBdl";

    private static final String TAG = UploadFeatureActivity.class.getSimpleName();

    // UI elements
    private ProgressBar progressBar;  // load data 10%, load Table 30%, upload track 30%, upload points 30%

    private ServiceFeatureTable mTrackServiceFeatureTable;
    private ServiceFeatureTable mPointServiceFeatureTable;

    private TrackResult trackResult;
    private PointResult pointResult;
    private SpatialReference spatialReference;

    private boolean finishFlag;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        // check network connection
        if (!isNetworkConnected()) {
            Log.d(TAG, "no internet");
            Toast.makeText(this, "Upload fail. Please check internet connection.", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // UI
        setContentView(R.layout.activity_upload_layer);
        progressBar = findViewById(R.id.progressBarUpload);
        progressBar.setMax(100);
        progressBar.setMin(0);

        // init variables
        finishFlag = false;

        // Extract data
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        Bundle bundleObject = extras.getBundle(GAME_RESULT_BDL_KEY);
        trackResult = (TrackResult) bundleObject.getSerializable(TRACK_OBJ_KEY);
        pointResult = (PointResult) bundleObject.getSerializable(POINT_OBJ_KEY);
        Log.d(TAG, "data loaded");

        progressBar.setProgress(10);

        // location services
        // authentication with an API key or named user is required to access basemaps and other
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.API_KEY);

        // create service feature table from URL
        mTrackServiceFeatureTable = new ServiceFeatureTable(getString(R.string.round_trip_layer_url));
        mPointServiceFeatureTable = new ServiceFeatureTable(getString(R.string.check_point_layer_url));

        FeatureLayer pointFeatureLayer = new FeatureLayer(mPointServiceFeatureTable);
        pointFeatureLayer.loadAsync();
        FeatureLayer trackFeatureLayer = new FeatureLayer(mTrackServiceFeatureTable);
        trackFeatureLayer.loadAsync();

        // After table loaded, ad features
        mPointServiceFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Point table Loaded");
                addPointFeature();
            }
        });

        mTrackServiceFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                addTrackFeature();
            }
        });

        // Create spacial reference
        spatialReference = SpatialReferences.getWgs84();

        progressBar.setProgress(progressBar.getProgress() + 30);
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    private void myFinish() {
        Log.d(TAG, "myFinish()");
        setResult(RESULT_OK);
        finish();
    }

    private void addPointFeature() {
        // create default attributes for the feature
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("arrival_timestamp", Long.toString(pointResult.getArrival_timestamp()));
        attributes.put("user_id", pointResult.getUserId());
        attributes.put("track_id", pointResult.getTrackId());
        attributes.put("checkpoint_name", pointResult.getPoint().getName());

        Point mapPoint = new Point(pointResult.getPoint().getLatitude(), pointResult.getPoint().getLongitude(), spatialReference);

        // creates a new feature using default attributes and point
        Feature feature = mPointServiceFeatureTable.createFeature(attributes, mapPoint);

        // check if feature can be added to feature table
        if (mPointServiceFeatureTable.canAdd()) {
            // add the new feature to the feature table and to server
            mPointServiceFeatureTable.addFeatureAsync(feature).addDoneListener(() -> applyEdits(mPointServiceFeatureTable));
        } else {
            runOnUiThread(() -> logToUser(true, getString(R.string.error_cannot_add_to_feature_table)));
        }
        Log.d(TAG, "Point added");
    }

    private void addTrackFeature() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("start_timestamp", Long.toString(trackResult.getStartTimestamp()));
        attributes.put("user_id", trackResult.getUserId());
        attributes.put("track_id", trackResult.getTrackId());
        attributes.put("reward", trackResult.getRewardName());
        attributes.put("distance", trackResult.getDistance());
        attributes.put("duration", trackResult.getDuration());
        attributes.put("average_speed", trackResult.getAvgSpeed());
        attributes.put("average_temp", trackResult.getAvgTemperature());

        PointCollection borderCAtoNV = new PointCollection(SpatialReferences.getWgs84());
        for (LonLatPoint pt : trackResult.getTrackPoints()) {
            borderCAtoNV.add(pt.getLongitude(), pt.getLatitude());
        }
        Polyline polyLine = new Polyline(borderCAtoNV);

        // creates a new feature using default attributes and point
        Feature feature = mTrackServiceFeatureTable.createFeature(attributes, polyLine);

        // check if feature can be added to feature table
        if (mTrackServiceFeatureTable.canAdd()) {
            // add the new feature to the feature table and to server
            mTrackServiceFeatureTable.addFeatureAsync(feature).addDoneListener(() -> applyEdits(mTrackServiceFeatureTable));
        } else {
            runOnUiThread(() -> logToUser(true, getString(R.string.error_cannot_add_to_feature_table)));
        }
        Log.d(TAG, "Track added");
    }

    /**
     * Sends any edits on the ServiceFeatureTable to the server.
     *
     * @param featureTable service feature table
     */
    private void applyEdits(ServiceFeatureTable featureTable) {

        // apply the changes to the server
        final ListenableFuture<List<FeatureEditResult>> editResult = featureTable.applyEditsAsync();
        editResult.addDoneListener(() -> {
            try {
                List<FeatureEditResult> editResults = editResult.get();
                // check if the server edit was successful
                if (editResults != null && !editResults.isEmpty()) {
                    if (!editResults.get(0).hasCompletedWithErrors()) {
                        runOnUiThread(() -> logToUser(false, getString(R.string.feature_added)));
                    } else {
                        throw editResults.get(0).getError();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                runOnUiThread(() -> logToUser(true, getString(R.string.error_applying_edits, e.getCause().getMessage())));
            } finally {
                progressBar.setProgress(progressBar.getProgress() + 30);
                // if the other one has been added -> finish(), if not, set the flag to true
                if (finishFlag) {
                    myFinish();
                } else {
                    finishFlag = true;
                }
            }
        });
    }

    /**
     * Shows a Toast to user and logs to logcat.
     *
     * @param isError whether message is an error. Determines log level.
     * @param message message to display
     */
    private void logToUser(boolean isError, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (isError) {
            Log.e(TAG, message);
        } else {
            Log.d(TAG, message);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mMapView.resume();
    }

    @Override
    protected void onPause() {
//        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
//        mMapView.dispose();
        super.onDestroy();
    }
}