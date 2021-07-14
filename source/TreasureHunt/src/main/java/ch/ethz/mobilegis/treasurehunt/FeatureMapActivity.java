package ch.ethz.mobilegis.treasurehunt;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.icu.text.SimpleDateFormat;
import android.icu.util.GregorianCalendar;
import android.net.ConnectivityManager;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.GeoElement;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;


/**
 * FeatureMapActivity.java
 *
 * Author: Bingxin Ke
 * Last edited: 2021-05-06
 *
 * Assignment 2 component
 *
 * Used to show tracks on the provided url (ArcGIS server Feature Table)
 *
 * Requires internet connection.

 * */


public class FeatureMapActivity extends AppCompatActivity {
    private static final String TAG = FeatureMapActivity.class.getSimpleName();

    // UI elements
    private MapView mMapView;
    private Spinner spinnerUserid;
    private ImageButton searchButton;

    // ArcGIS Map
    private ArcGISMap map;
    private Callout mCallout;

    // Feature collections
    private ArrayList<Feature> allFeatures;
    private Set<Integer> userIdSet;
    private ArrayList<String> userIdStrings;
    private ArrayList<Feature> lastDisplayFeatures;
    private String currentUserId = null;
    private String selectedUserId = null;

    private ServiceFeatureTable mServiceFeatureTable;
    private FeatureLayer featureLayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        // check network connection
        if (!isNetworkConnected()) {
            Log.d(TAG, "no internet");
            Toast.makeText(this, "Please check internet connection.", Toast.LENGTH_LONG).show();
//            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        setContentView(R.layout.activity_feature_map);

        // UI elements
        spinnerUserid = (Spinner) findViewById(R.id.spinnerUserid);
        mMapView = findViewById(R.id.mapView);
        searchButton = (ImageButton) findViewById(R.id.imageButtonSearch);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSearchButtonClicked();
            }
        });

        allFeatures = new ArrayList<>();
        userIdSet = new HashSet<>();
        userIdStrings = new ArrayList<>();
        lastDisplayFeatures = new ArrayList<>();

        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.API_KEY);

        // create an ArcGISMap with a topographic basemap
        map = new ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC);
        // set the ArcGISMap to the MapView
        mMapView.setMap(map);
        // set a viewpoint
        mMapView.setViewpoint(new Viewpoint(47.408486, 8.508056, 10000));
        // get the callout that shows attributes
        mCallout = mMapView.getCallout();
        // create the service feature table
        mServiceFeatureTable = new ServiceFeatureTable(getResources().getString(R.string.round_trip_layer_url));
        // create the feature layer using the service feature table
        featureLayer = new FeatureLayer(mServiceFeatureTable);

        // add the layer to the map
        map.getOperationalLayers().add(featureLayer);

        initSpinner();

        // set an on touch listener to listen for click events
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @SuppressLint("ClickableViewAccessibility")


            @Override
            public void onLongPress(MotionEvent e) {
                // remove any existing callouts
                if (mCallout.isShowing()) {
                    mCallout.dismiss();
                }
                // get the point that was clicked and convert it to a point in map coordinates
                final Point screenPoint = new Point(Math.round(e.getX()), Math.round(e.getY()));
                // create a selection tolerance
                int tolerance = 10;
                // use identifyLayerAsync to get tapped features
                final ListenableFuture<IdentifyLayerResult> identifyLayerResultListenableFuture = mMapView
                        .identifyLayerAsync(featureLayer, screenPoint, tolerance, false, 1);
                identifyLayerResultListenableFuture.addDoneListener(() -> {
                    try {
                        IdentifyLayerResult identifyLayerResult = identifyLayerResultListenableFuture.get();
                        // create a textview to display field values
                        TextView calloutContent = new TextView(getApplicationContext());
                        calloutContent.setTextColor(Color.BLACK);
                        calloutContent.setSingleLine(false);
                        calloutContent.setVerticalScrollBarEnabled(true);
                        calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                        calloutContent.setMovementMethod(new ScrollingMovementMethod());
                        calloutContent.setLines(5);
                        for (GeoElement element : identifyLayerResult.getElements()) {
                            Feature feature = (Feature) element;
                            // create a map of all available attributes as name value pairs
                            Map<String, Object> attr = feature.getAttributes();
                            Set<String> keys = attr.keySet();
                            for (String key : keys) {
                                Object value = attr.get(key);
                                String stringToShow;

                                switch (key) {
                                    case "duration":
                                        stringToShow = String.format("Duration: %.1f s", value);
                                        break;
                                    case "distance":
                                        stringToShow = String.format("Distance: %.2f m", value);
                                        break;
                                    case "average_speed":
                                        stringToShow = String.format("Average Speed: %.2f km/h", value);
                                        break;
                                    case "reward":
                                        stringToShow = "Reward: " + value;
                                        break;
                                    case "average_temp":
                                        stringToShow = String.format("Average Temperature: %.1f °C", value);
                                        break;
                                    default:
                                        stringToShow = null;
                                }

                                if (null != stringToShow) {
                                    calloutContent.append(stringToShow + "\n");
                                }
                            }
                            // center the mapview on selected feature
                            Envelope envelope = feature.getGeometry().getExtent();
                            mMapView.setViewpointGeometryAsync(envelope, 200);
                            // show callout
                            mCallout.setLocation(envelope.getCenter());
                            mCallout.setContent(calloutContent);
                            mCallout.show();
                        }
                    } catch (Exception e1) {
                        Log.e(TAG, "Select feature failed: " + e1.getMessage());
                    }
                });
                super.onLongPress(e);
            }

            /**
             * Single touch to hide pop-up mCallout
             * */
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                mCallout.dismiss();
                return super.onSingleTapConfirmed(e);
            }
        });
    }

    /**
     * Check network connection
     *
     * @return true -> can access internet; false -> internet not connected
     * */
    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    /**
     * Get all features, update userIdSet, update spinner
     */
    private void initSpinner() {
        Log.d(TAG, "initSpinner()");
        // Get all features from table
        //create query parameters
        QueryParameters queryParams = new QueryParameters();
        // 1=1 will give all the features from the table
        queryParams.setWhereClause("1=1");
        //query feature from the table
        final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(queryParams);
        // add done loading listener to fire when the selection returns
        future.addDoneListener(() -> {
            try {
                // clear lists
                allFeatures.clear();
                userIdSet.clear();

                // call get on the future to get the result
                FeatureQueryResult result = future.get();
                // check there are some results
                Iterator<Feature> resultIterator = result.iterator();
                while (resultIterator.hasNext()) {
                    // get the extent of the first feature in the result to zoom to
                    Feature feature = resultIterator.next();
//                    Log.d(TAG, "feature: " + feature.toString());
                    allFeatures.add(feature);
                    Map<String, Object> attr = feature.getAttributes();
//                    Log.d(TAG, "attribute: " + attr.toString());
                    int userId = (Integer) attr.get("user_id");
//                    Log.d(TAG, "user_id = " + userId);
                    userIdSet.add(userId);
                }

                // update last features
                lastDisplayFeatures = allFeatures;

                // Update user id
                ArrayList<Integer> tempUserIds = new ArrayList<Integer>();
                tempUserIds.addAll(userIdSet);
                Collections.sort(tempUserIds);
                userIdStrings.add(0, getString(R.string.user_id_all_uers));
                for (int id : tempUserIds) {
                    userIdStrings.add(Integer.toString(id));
                }
                SpinnerAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, userIdStrings);
                spinnerUserid.setAdapter(adapter);

                // Set spinner listener
                spinnerUserid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedUserId = userIdStrings.get(position);
                        Log.d(TAG, "select user id: " + selectedUserId);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

                Toast.makeText(this, "User ID loaded", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Fail to load User ID, please retry.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "error: " + e.toString());
            }
        });
    }

    /**
     * Response when search button is clicked.
     *
     * Select corresponding features, referring to selected userid.
     *
     * */
    private void onSearchButtonClicked() {
        Log.d(TAG, "onSearchButtonClicked(), selectedUserId = " + selectedUserId);
        if (null == selectedUserId) {
            return;
        } else if (getString(R.string.user_id_all_uers).equals(selectedUserId)) {
            // all features
            updateAllFeatures();
        } else {
            updateFeatures(selectedUserId);
        }
    }

    /**
     * Filter features of a specific user.
     *
     * @param userId : the user id to filter.
     * */
    private void updateFeatures(String userId) {
        Log.d(TAG, "updateFeatures()");
        queryUpdateFeatures("user_id=" + userId);
    }

    /**
     * Show all features.
     *
     * */
    private void updateAllFeatures() {
        Log.d(TAG, "updateAllFeatures()");
        queryUpdateFeatures("1=1");
    }


    /**
     * Query from FeatureTable and show.
     *
     * Query -> Hide previous features -> Show selected features -> Zoom to features.
     *
     * @param queryPhase : Query sentence, to be put in setWhereClause()
     * */
    private void queryUpdateFeatures(String queryPhase) {
        Log.d(TAG, "queryUpdateFeatures(\"" + queryPhase + "\")");
        // Get all features from table
        //create query parameters
        QueryParameters queryParams = new QueryParameters();
        // 1=1 will give all the features from the table
        queryParams.setWhereClause(queryPhase);
        //query feature from the table
        final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(queryParams);
        // add done loading listener to fire when the selection returns
        ArrayList<Feature> selectedFeatures = new ArrayList<>();
        future.addDoneListener(() -> {
            try {
                // call get on the future to get the result
                FeatureQueryResult result = future.get();
                // check there are some results
                Iterator<Feature> resultIterator = result.iterator();
                while (resultIterator.hasNext()) {
                    // get the extent of the first feature in the result to zoom to
                    Feature feature = resultIterator.next();
                    selectedFeatures.add(feature);
                }

                // hide last features
                featureLayer.setFeaturesVisible(lastDisplayFeatures, false);
                // display selected features
                featureLayer.setFeaturesVisible(selectedFeatures, true);
                // update last features
                lastDisplayFeatures = selectedFeatures;
                // center the mapview on the last feature in list
                Envelope envelope = getMaxEnvelope(selectedFeatures);
                mMapView.setViewpointGeometryAsync(envelope, 800);

                Toast.makeText(this, "Tracks loaded", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Features loaded for userId =" + selectedUserId);
            } catch (Exception e) {
                Toast.makeText(this, "Fail to load Tracks, please retry.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Load feature error: " + e.toString());
            }
        });
    }

    /**
     * Calculate max bounding envelope of given features.
     *
     * @param featureArrayList : input features, should share the same SpatialReference
     * @return max bounding envelope, with the same SpatialReference as given one
     * */
    private Envelope getMaxEnvelope(ArrayList<Feature> featureArrayList) {
        double maxX = -1e10;
        double maxY = -1e10;
        double minX = 1e10;
        double minY = 1e10;
        SpatialReference spatialReference = null;

        for (Feature feature : featureArrayList) {
            if (null == feature.getGeometry() || null == feature) {
                continue;
            }
            Envelope tempEnvelope = feature.getGeometry().getExtent();
            spatialReference = tempEnvelope.getSpatialReference();
            maxX = Math.max(tempEnvelope.getXMax(), maxX);
            maxY = Math.max(tempEnvelope.getYMax(), maxY);
            minX = Math.min(tempEnvelope.getXMin(), minX);
            minY = Math.min(tempEnvelope.getYMin(), minY);
        }
        return new Envelope(minX, minY, maxX, maxY, spatialReference);
    }

    /* Empty override functions */
    @Override
    protected void onPause() {
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }
}