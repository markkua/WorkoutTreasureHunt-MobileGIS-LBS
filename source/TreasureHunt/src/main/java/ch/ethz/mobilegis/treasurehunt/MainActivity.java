package ch.ethz.mobilegis.treasurehunt;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


/**
 * MainActivity.java
 *
 * Author: Bingxin Ke
 * Last edited: 2021-05-06
 *
 * This is the assignment 1 of [Mobile GIS and Location-Based Services FS2021, ETH]
 * This app aims at encouraging the users to get out of their home and do a round trip running or
 *      walking near their home, using "treasure hunting" to improve attractiveness.
 * This is the main activity of this app.
 *
 * Main functions:
 * 1. checkpoint selection (checkpoints are predefined in the raw file)
 * 2. treasure hunt guide (round trip, implemented in NavigateActivity.java)
 *
 *
 * Required permissions:
 *  - ACCESS_COARSE_LOCATION
 *  - ACCESS_FINE_LOCATION
 *  - ACCESS_BACKGROUND_LOCATION
 *  - WRITE_EXTERNAL_STORAGE
 * */


public class MainActivity extends AppCompatActivity {
    public static final String CHANNEL_ID = "MY_CHANNEL_ID";
    public static final Float GEOFENCE_RADIUS = 10f;
    public static final int NAVIGATE_REQUEST_CODE = 201;
    public static final int RESULT_OK = 0;
    public static final int RESULT_CANCEL = -1;
    private static final String TAG = MainActivity.class.getSimpleName();

    public static TrackResult globalTrackResult = null;

    // Set up location-related variables.
    private Geofence geofence;
    private CheckPoint selectedCheckPoint;

    // UI components (start page)
    private TextView txtLog;
    private TextView coordText;
    private Button buttonStart;
    private Button buttonMap;
    private Button buttonAR;  // Assignment 3
    private ImageButton buttonInfo; // Assignment 3
    private Spinner spinnerCheckpoint;

    // UI variables
    private ArrayList<CheckPoint> checkPoints;

    // Flags
    private boolean isAssetComplete;

    /*********************************** Assignment 3 ************************************/
    private void onButtonAR() {
        Log.i(TAG, "buttoAR clicked");

        Bundle bundleObject = new Bundle();

        Intent arIntent = new Intent(this, ARActivity.class);
        startActivity(arIntent);
    }

    private void onButtonInfo() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.dialog_info_title)
                .setMessage(R.string.dialog_info_message)
                .setCancelable(true)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        }
                );
        dialog.show();

    }


    /*********************************** Assignment 2 ************************************/
    private void onButtonMap() {
        Log.i(TAG, "buttonMap clicked");

        Bundle bundleObject = new Bundle();

        Intent mapIntent = new Intent(this, FeatureMapActivity.class);
        startActivity(mapIntent);
    }

    /**
     * Listen to [Back] key click, remind to share when exit
     *
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Disable back key
        if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_DOWN)) {
            shareResultThenFinish();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Share track result. If there is a result, ask whether to share.
     *
     * Call finish() whatever user chooses.
     *
     * */
    private void shareResultThenFinish() {
        if (null == globalTrackResult) {
            Log.d(TAG, "globalTrackResult == null");
            finish();
            return;
        }
        Log.d(TAG, "shareResult");
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.share_dialog_title)
                .setMessage(R.string.share_dialog_message)
                .setCancelable(true)
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(getApplicationContext(), "Share canceled", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(TAG, "Yes clicked");
                                Intent sendIntent = new Intent();
                                sendIntent.setAction(Intent.ACTION_SEND);
                                String share_text = getString(R.string.social_share_text,
                                        globalTrackResult.getUserId(), globalTrackResult.getDuration(),
                                        globalTrackResult.getDistance(), globalTrackResult.getRewardName());
                                Log.d(TAG, share_text);
                                sendIntent.putExtra(Intent.EXTRA_TEXT, share_text);
                                sendIntent.setType("text/plain");

                                Intent shareIntent = Intent.createChooser(sendIntent, "Share your result");
                                startActivity(shareIntent);
                                finish();
                            }
                        }
                );
        dialog.show();
    }


    /*********************************** Assignment 1 ************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Elements
        txtLog = (TextView) findViewById(R.id.spinnerText);
        coordText = (TextView) findViewById(R.id.coordText);
        buttonStart = (Button) findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonStart();
            }
        });

        buttonMap = (Button) findViewById(R.id.buttonMap);
        buttonMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonMap();
            }
        });

        buttonAR = (Button) findViewById(R.id.buttonAR);  // Assignment 3
        buttonAR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonAR();
            }
        });

        buttonInfo = (ImageButton) findViewById(R.id.imageButtonInfo);
        buttonInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonInfo();
            }
        });

        spinnerCheckpoint = (Spinner) findViewById(R.id.spinnerCheckpoint);

        createNotificationChannel();

        // Check assets
        if (readCheckpointCSV()) {
            isAssetComplete = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        // Request permissions
        checkAndRequestPermissions();

        if (isAssetComplete) {
            Log.d(TAG, checkPoints.toString());
            setSpinnerResource();
            selectedCheckPoint = checkPoints.get(0);
        } else {
            // warning if false==isAssetComplete
            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Missing file")
                    .setMessage("File missing. Please reinstall the app.")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }
                    );
            dialog.show();
        }
    }

    /**
     * Call back function when [Start] button is clicked.
     * Start NavigateActivity and pass selected geofence to it.
     * Extract geofence data using:
     *       [intent].getExtras().getBundle("geofenceBundle").getSerializable("geofence")
     *
     * */
    private void onButtonStart() {
        Log.i(TAG, "buttonStart clicked");

        Bundle bundleObject = new Bundle();
        bundleObject.putSerializable("geofence", geofence);

        Intent naviIntent = new Intent(this, NavigateActivity.class);
//        naviIntent.addCategory(Intent.CATEGORY_HOME);
//        naviIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        naviIntent.putExtra("geofenceBundle", bundleObject);
        startActivity(naviIntent);
//        startActivityForResult(naviIntent, NAVIGATE_REQUEST_CODE);
    }


    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume()");
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(TAG, "onPause()");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    /**
     * We use this function to check for permissions, and pop up a box asking for them,
     * in case a user hasn't given them yet.
     */
    private void checkAndRequestPermissions() {
        requestLocationPermissions();
        requestStoragePermission();
    }

    /**
     * Request WRITE_EXTERNAL_STORAGE permission, for saving result
     * */
    private void requestStoragePermission(){
        if(ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.storage_permission_dialog_title)
                    .setMessage(R.string.storage_permision_dialog_msg)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                            0);
                                }
                            }
                    );
            dialog.show();
        }
    }

    /**
     * Request coarse and fine location permissions, then request background location permission.
     * If already got permissions then do nothing. If not, popup a dialog to remind, then request.
     *
     * NOTE: Make sure only request background location permission after  having fine and coarse
     *      location permissions. Otherwise, no permission dialog will show up, and it will fail.
     *      More details in: https://developer.android.com/training/location/background
     */
    private void requestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            requestBackgroundLocationPermission();
        } else {
            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.location_fine_permission_dialog_title)
                    .setMessage(R.string.location_fine_permission_dialog_msg)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Log.d(TAG, "dialog [OK] clicked");
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION},
                                            0);
                                    requestBackgroundLocationPermission();
                                }
                            }
                    );
            dialog.show();
        }
    }

    /**
     * Request background location permission. Called by requestLocationPermissions.
     * If already got permissions then do nothing. If not, popup a dialog to remind, then request.
     * */
    private void requestBackgroundLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Don't have background location permission
            // Remind and request background location permission
            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.location_bg_permission_dialog_title)
                    .setMessage(R.string.location_bg_permission_dialog_msg)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Log.d(TAG, "dialog [OK] clicked");
                                    ActivityCompat.requestPermissions(
                                            MainActivity.this,
                                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                            0);
                                }
                            }
                    );
            dialog.show();
        }
    }

    /**
     * Create a notification Channel on >= Android 8.0
     * Source: https://developer.android.google.cn/training/notify-user/build-notification.html#java
     */
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notif_channel_id);
            String description = getString(R.string.notif_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Read checkpoints from csv file "res/raw/checkpoints.csv"
     * */
    private Boolean readCheckpointCSV() {
        checkPoints = new ArrayList<>();
        InputStream inputStream = getResources().openRawResource(R.raw.checkpoints);
        boolean tempFlag = false;
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(
                    inputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = "";
            bufferedReader.readLine();  // skip first line
            while ((line = bufferedReader.readLine()) != null) {
                String[] values = line.split(";");
                checkPoints.add(new CheckPoint(values[0], Float.parseFloat(values[1]), Float.parseFloat(values[2])));
            }
            tempFlag = true;
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            tempFlag = false;
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "Load csv file: " + (tempFlag ? "Success" : "Fail"));
        return tempFlag;
    }

    /**
     * Bind spinner with checkpoints
     * */
    private void setSpinnerResource() {
        // Bind checkpoint data
        SpinnerAdapter adapter = new ArrayAdapter<CheckPoint>(this, android.R.layout.simple_spinner_dropdown_item, checkPoints);
        spinnerCheckpoint.setAdapter(adapter);

        // Set selected action
        spinnerCheckpoint.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                CheckPoint cp = checkPoints.get(position);
                coordText.setText(String.format(getString(R.string.coordText_format), cp.getLongitude(), cp.getLatitude()));
//                geofences.clear();
//                geofences.add(new Geofence(cp.getName(), cp.getLatitude(), cp.getLongitude(), GEOFENCE_RADIUS));
                geofence = new Geofence(cp.getName(), cp.getLatitude(), cp.getLongitude(), GEOFENCE_RADIUS);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });
    }
}