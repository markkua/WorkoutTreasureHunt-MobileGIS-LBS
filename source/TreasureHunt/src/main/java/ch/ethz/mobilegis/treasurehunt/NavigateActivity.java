package ch.ethz.mobilegis.treasurehunt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import static java.lang.Math.abs;

/**
 * NavigateActivity.java
 *
 * Author: Bingxin Ke
 * Last edited: 2021-05-06
 *
 * This activity is used to help user finish the "treasure hunt" round trip:
 * 0. provide activity information: destination, temperature, speed;
 * 1. guide the user from start point to selected checkpoint(Treasure Location), shown as direction
 * and distance;
 * 2. remind the user when arrive checkpoint;
 * 3. guide the user from checkpoint to start point;
 * 4. calculate activity result and corresponding reward;
 * 5. save result to csv file;
 */


public class NavigateActivity extends AppCompatActivity implements LocationListener, SensorEventListener {
    public static final int USER_ID = 5;  // Assignment 2
    private static final double ZERO_THRESHOLD = 1e-5;
    private static final long LOCATION_INTERVAL = 1000;
    private static final float LOCATION_MIN_DIST = 2f;
    private static final float DEFAULT_TEMPERATURE = 20.0f;
    private static final String TAG = NavigateActivity.class.getSimpleName();
    private static final int NOTIF_ID_ARRIVE_CHECKPOINT = 0;  // notification id, used to cancel notification
    private static final int NOTIF_ID_ARRIVE_START = 1;
    private static final int UPLOAD_REQUEST_CODE = 2000;

    // UI components (navigate page)
    private Button buttonStop;
    private TextView textSpeed;
    private TextView textDest;
    private TextView textTemp;
    private TextView textDist;
    private TextView textDirection;
    private ImageView imageNavi;

    // Location-related variables
    private LocationManager locationManager;
    private Geofence targetGeofence;  // used only for result output.
    private Geofence geofence;
    private Double lastDist;
    private float targetBearing;
    private Location startLocation;
    private Location lastLocation;
    private long lastTime;

    // Sensor-related variables
    Sensor magnetic;
    Sensor accelerometer;
    Sensor temperature;
    private SensorManager sensorManager;
    float[] accelerometerValues = new float[3];
    float[] magneticValues = new float[3];
    private float phoneOrientation;
    private ArrayList<Float> temperatures;

    // reward-related variables
    private long startTime;
    private Double totalDist;
    private double avgSpeed;
    private double avgTemp;
    private double duration;
    private Reward reward;
    private ArrayList<LonLatPoint> trackPoints;  // store track points (Assignment 2)
    TrackResult trackResult; // Assignment 2

    private Boolean isReturnTrip;

    private Intent intent;
    private Bundle extras;
    NotificationManager mNotificationManager;

    /********************************** Assignment 2 functions ***********************************/

    /**
     * Start a new activity to upload result.
     *
     * Called in remindArrival(), when round trip is finished.
     * */
    private void uploadFeatures() {
        Log.d(TAG, "uploadTrack()");
        // prepare data
        int trackId = (int) (Math.random() * 99999);
        trackResult = new TrackResult(trackPoints, startTime, USER_ID, trackId, reward.getPureName(), totalDist,
                duration, avgSpeed, avgTemp);

        // Pass result to MainActivity (Tried setResult(), but MainActivity automatically stop when its onActivityResult() is called)
        MainActivity.globalTrackResult = trackResult;

        CheckPoint checkPoint = new CheckPoint(targetGeofence.getName(), targetGeofence.getLongitude(), targetGeofence.getLatitude());
        PointResult pointResult = new PointResult(checkPoint, lastTime, USER_ID, trackId);

        Bundle bundleObject = new Bundle();
        bundleObject.putSerializable(UploadFeatureActivity.TRACK_OBJ_KEY, trackResult);
        bundleObject.putSerializable(UploadFeatureActivity.POINT_OBJ_KEY, pointResult);
        Intent uploadIntent = new Intent(this, UploadFeatureActivity.class);
        uploadIntent.putExtra(UploadFeatureActivity.GAME_RESULT_BDL_KEY, bundleObject);
        uploadIntent.putExtra(UploadFeatureActivity.TRACK_OBJ_KEY, trackResult);
        uploadIntent.putExtra(UploadFeatureActivity.POINT_OBJ_KEY, pointResult);
//        startActivity(uploadIntent);
        startActivityForResult(uploadIntent, UPLOAD_REQUEST_CODE);
    }

    /**
     * Wait for the upload result.
     * When upload finish / fail, return to MainActivity.
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult(), requestCode=" + requestCode + " , resultCode=" + resultCode);
        if (requestCode == UPLOAD_REQUEST_CODE) {
            Log.d(TAG, "onActivityResult()");
            finish();
        }
    }

    /********************************** Assignment 1 functions ***********************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        setContentView(R.layout.activity_navigate);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        isReturnTrip = false;

        buttonStop = (Button) findViewById(R.id.buttonStop);
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonStop();
            }
        });
        textSpeed = (TextView) findViewById(R.id.textSpeed);
        textDest = (TextView) findViewById(R.id.textDest);
        textTemp = (TextView) findViewById(R.id.textTemp);
        textDist = (TextView) findViewById(R.id.textDist);
        textDirection = (TextView) findViewById(R.id.textDirection);
        imageNavi = (ImageView) findViewById(R.id.imageNavi);

        lastDist = Double.MAX_VALUE;
        totalDist = 0.0;
        phoneOrientation = 0.0f;
        temperatures = new ArrayList<Float>();
        trackPoints = new ArrayList<LonLatPoint>();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        if (null == geofence) {
            try {
                intent = getIntent();
                extras = intent.getExtras();
                setGeofence();

                Log.d(TAG, "geofence=" + geofence.toString());
                textDest.setText(geofence.getName());
                Log.i(TAG, "Navigate Activity started");

                Toast.makeText(getApplicationContext(),
                        R.string.active_start_success_toast,
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                Toast.makeText(getApplicationContext(),
                        R.string.active_start_fail_toast,
                        Toast.LENGTH_SHORT).show();
            }
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        updateTemperature(DEFAULT_TEMPERATURE);  // set a default temperature;

        // Register location and sensor listeners
        registerListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");

        moveTaskToBack(true);
//        Toast.makeText(getApplicationContext(), "Hunting stopped.", Toast.LENGTH_SHORT).show();
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        pauseListeners();
        // remove notification
        mNotificationManager.cancel(NOTIF_ID_ARRIVE_CHECKPOINT);
        mNotificationManager.cancel(NOTIF_ID_ARRIVE_START);
        super.onDestroy();
    }

    /**
     * Register locationListener and sensorListener(s)
     */
    private void registerListeners() {
        // make sure activity get the permission
        if (PackageManager.PERMISSION_GRANTED
                != ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                && PackageManager.PERMISSION_GRANTED
                != ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                || PackageManager.PERMISSION_GRANTED
                != ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            Log.e(TAG, "No permission");
            Toast.makeText(getApplicationContext(),
                    "No permission, please restart app.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL,
                LOCATION_MIN_DIST, this);
//        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
//                LOCATION_INTERVAL, LOCATION_MIN_DIST, this);

        sensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_GAME);
    }

    /**
     * Remove locationListener and unregister sensorListener(s)
     */
    private void pauseListeners() {
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this, magnetic);
        sensorManager.unregisterListener(this, accelerometer);
        sensorManager.unregisterListener(this, temperature);
    }

    /**
     * Call back function of button [Stop] clicked. Remind before exit activity
     */
    private void onButtonStop() {
        Log.i(TAG, "buttonStop clicked");

        // remind dialog
        AlertDialog.Builder dialog = new AlertDialog.Builder(NavigateActivity.this);
        dialog.setTitle("Stop");
        dialog.setMessage(R.string.stop_activity_dialog_msg);
        dialog.setCancelable(true);
        dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
//                Log.d(TAG, "dialog [Yes] clicked");
                finish();
            }
        });
        dialog.setNegativeButton("Cancel", (dialog1, which) -> {
        });
        dialog.show();
//        Log.d(TAG, "remind dialog showed");
    }

    /**
     * Extract geofence from extra bundle data, and save target geofence
     */
    private void setGeofence() {
        Bundle bundleObject = extras.getBundle("geofenceBundle");
        geofence = (Geofence) bundleObject.getSerializable("geofence");
        targetGeofence = geofence.clone();
        lastDist = Double.MAX_VALUE;
    }

    /**
     * Listen to locationChanged, update view when location changed.
     * Implement geofence.
     */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d(TAG, "Location=" + location.toString());

        // store location (Assignment 2)
        trackPoints.add(new LonLatPoint(location.getLongitude(), location.getLatitude()));

        // Start location
        if (null == startLocation) {
            startLocation = location;
            startTime = System.currentTimeMillis();
            lastLocation = location;
            lastTime = startTime;
            Log.d(TAG, "startLocation=" + location.toString() + "startTime=" + startTime);
        }

        // Distance
        double distance = geofence.getLocation().distanceTo(location);
        totalDist += location.distanceTo(lastLocation);  // accumulate distances
        Log.d(TAG, "distance=" + distance);
        textDist.setText(String.format("%.1fm", distance));

        // Speed. Use GPS data first, if no speed data, calculate from last location.
        double speed = location.getSpeed();  // m/s
        long currentTime = System.currentTimeMillis();
        // calculate from 2 locations
        if ((abs(speed) < ZERO_THRESHOLD && null != lastLocation)) {
            double tempDist = location.distanceTo(lastLocation);
            speed = tempDist / (currentTime - lastTime) * 1000.0;
        }
        lastTime = currentTime;
        textSpeed.setText(String.format("%.1f", speed));

        // Direction
        targetBearing = location.bearingTo(geofence.getLocation());
        textDirection.setText(String.format("%.0f" + getString(R.string.degree), targetBearing));
        rotateImageNavi();

        /* In the case new distance is smaller than the radius of the fence, and the old one is
         bigger, we are entering the geofence.*/
        if (distance < geofence.getRadius() && lastDist > geofence.getRadius()) {
            Log.d(TAG, "Enter geofence: " + geofence.getName());
            remindArrival();
        } else if (distance > geofence.getRadius() && lastDist < geofence.getRadius()) {
            // In the opposite case, we must be leaving the geofence.
            Log.d(TAG, "Leave geofence: " + geofence.getName());
        }
        lastDist = distance;
        lastLocation = location;
    }

    /**
     * Transfer speed to pace, i.e. from m/s to sec/km
     *
     * @param meterPsec: speed in m/second
     * @return pace in sec/km
     */
    private double mps2spkm(double meterPsec) {
        if (abs(meterPsec) < ZERO_THRESHOLD) {
            return 0.0;
        }
        return 1000.0 / meterPsec;
    }

    /**
     * Remind the user when enter geofence.
     * Provide different remind for outbound and return trip.
     */
    private void remindArrival() {
        if (!isReturnTrip) {
            // Outbound
            AlertDialog.Builder dialog = new AlertDialog.Builder(NavigateActivity.this)
                    .setTitle("Go Back To Start Point")
                    .setMessage(getString(R.string.destination_arrival_remind_msg))
                    .setCancelable(false)
                    .setPositiveButton("GO", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Go back to start dialog [Yes] clicked");
                        }
                    });
            dialog.show();

            Toast.makeText(getApplicationContext(), R.string.destination_arrival_toast,
                    Toast.LENGTH_SHORT).show();
            // send notification
            sendNotification("Arrive at" + geofence.getName(),
                    getString(R.string.arrival_notification_message), NOTIF_ID_ARRIVE_CHECKPOINT);

            setGeofenceToStartPoint();

            isReturnTrip = true;
            Log.d(TAG, "isBackTrip=" + isReturnTrip.toString());
        } else {
            // Back trip
            pauseListeners();
            reward = calculateReward();

            // send notification
            mNotificationManager.cancel(NOTIF_ID_ARRIVE_CHECKPOINT);
            sendNotification("Activity finished",
                    getString(R.string.finish_notification_message), NOTIF_ID_ARRIVE_CHECKPOINT);

            // write result
            saveResultToFile();

            // Show result and reward
            Toast.makeText(getApplicationContext(), R.string.finish_toast,
                    Toast.LENGTH_SHORT).show();
            String celsius = getString(R.string.celsius);
            String msg = String.format(getString(R.string.finish_dialog_msg), totalDist, avgSpeed,
                    avgTemp, celsius, reward.getName());
            // dialog
            AlertDialog.Builder dialog = new AlertDialog.Builder(NavigateActivity.this)
                    .setTitle("Finish")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Finish dialog [Yes] clicked");
                            uploadFeatures();
//                            shareResult();
                        }
                    });
            dialog.show();
        }
    }


    /**
     * Send notification with sound and vibaration
     *
     * @param title:   title content of the notification
     * @param message: body message of the notification
     * @param id:      notification id, to identify this notification
     */
    private void sendNotification(String title, String message, int id) {
        Log.d(getClass().getSimpleName(), "sending notification");

        /*
          Open problem: how to return to this activity when notification is clicked,
           instead of start a new activity
         */

        // Get a notification builder that's compatible with platform versions >= 4
        NotificationCompat.Builder builder = new NotificationCompat.Builder(NavigateActivity.this, MainActivity.CHANNEL_ID);

        // Define the notification settings.
        builder.setSmallIcon(R.drawable.ic_launcher)
                // In a real app, you may want to use a library like Volley
                // to decode the Bitmap.
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_launcher))
                .setColor(Color.RED)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentTitle(title)
                .setContentText(message);
//                .setContentIntent(notificationPendingIntent);

        // Dismiss notification once the user touches it.
        builder.setAutoCancel(true);

        // Issue the notification
        mNotificationManager.notify(id, builder.build());
    }

    /**
     * Set geofence to start point and update view, i.e. navigate user back to start point.
     */
    private void setGeofenceToStartPoint() {
        pauseListeners();
        geofence = new Geofence("Start Point", startLocation.getLatitude(),
                startLocation.getLongitude(), MainActivity.GEOFENCE_RADIUS);
        textDest.setText(geofence.getName());
        lastDist = Double.MAX_VALUE;
        Log.d(TAG, "setGeofenceToStartPoint()");
        registerListeners();
    }

    /**
     * Determine which reward should be given, considering avgSpeed, totalDistance, avgTemperature.
     *
     * @return: result reward
     */
    private Reward calculateReward() {
        // average speed
        Log.d(TAG, "finishTime=" + lastTime + " startTime=" + startTime);
        duration = (lastTime - startTime) / 1000.0;
        avgSpeed = totalDist / duration * 3.6;  // km/h
        Log.d(TAG, "totalDist=" + totalDist + " avgSpeed=" + avgSpeed);
        // average temperature
        float sumTemp = 0;
        for (float f : temperatures) {
            sumTemp += f;
        }
        avgTemp = sumTemp / temperatures.size();

        // judge reward
        if (avgSpeed >= 4.0 && avgSpeed < 6.0 && totalDist <= 1 && avgTemp > 4.0 && avgTemp < 20) {
            return Reward.Peach;
        } else if (avgSpeed >= 4.0 && avgSpeed < 6.0 && totalDist > 1 && avgTemp > 20) {
            return Reward.Watermelon;
        } else if (avgSpeed >= 6.0 && avgSpeed < 8.0 && totalDist > 0 && avgTemp > 20) {
            return Reward.IceCream;
        } else if (avgSpeed >= 8.0 && totalDist > 1 && avgTemp > 4.0 && avgTemp < 20) {
            return Reward.Banana;
        } else {
            return Reward.Apple;
        }
    }

    /**
     * Overload of saveResultToFile with default filename.
     */
    private void saveResultToFile() {
        saveResultToFile("output.csv");
    }


    /**
     * Save result to external storage.
     *
     * @param filename: filename to save result, suggested to end with .csv
     */
    private void saveResultToFile(String filename) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            try {
                File file = new File(getExternalFilesDir(null), filename);
                FileOutputStream outputStream = new FileOutputStream(file, true);
                PrintWriter writer = new PrintWriter(outputStream);

                ArrayList<String> data = new ArrayList<String>();
                data.add(String.valueOf(startTime));
                data.add(String.format("%.8f", startLocation.getLongitude()));
                data.add(String.format("%.8f", startLocation.getLatitude()));
                data.add(String.format("%.8f", targetGeofence.getLongitude()));
                data.add(String.format("%.8f", targetGeofence.getLatitude()));
                data.add(String.format("%.1f", totalDist));
                data.add(String.format("%.1f", (lastTime - startTime) / 1000.0));
                data.add(String.format("%.2f", avgSpeed));
                data.add(String.format("%.1f", avgTemp));
                data.add(reward.getPureName());

                writer.println(String.join(",", data));

                writer.close();
                outputStream.close();
                Log.d(TAG, "File Saved :  " + file.getPath());
            } catch (IOException e) {
                Log.e(TAG, "Fail to write file");
            }
        } else {
            Log.e(TAG, "SD card not mounted");
        }
    }

    /**
     * Listen to sensor change event:
     * 1. rotate navigate image (compass) according to current bearing and target direction;
     * 2. update temperature;
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Phone orientation. Reference: https://blog.csdn.net/Tomi_En/article/details/48245023
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values.clone();
        }
        float[] R = new float[9];
        float[] values = new float[3];
        SensorManager.getRotationMatrix(R, null, accelerometerValues,
                magneticValues);
        SensorManager.getOrientation(R, values);
        phoneOrientation = (float) Math.toDegrees(values[0]);
//        Log.d(TAG, "phoneOrientation=" + phoneOrientation);
        rotateImageNavi();

        // Temperature
        if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            float temperature = event.values[0];  // unit: celsius
            updateTemperature(temperature);
            Log.d(TAG, "Temperature=" + temperature);
        }
    }

    /**
     * Rotate direction indicator image. Using phoneOrientation and targetBearing.
     * Called when either targetBearing changed or phoneOrientation changed.
     */
    private void rotateImageNavi() {
        float rotateDegree = -phoneOrientation + targetBearing;
        imageNavi.setRotation(rotateDegree);
    }

    /**
     * Update temperature textView, and add temperature to ArrayList (used for average).
     *
     * @param temperature: float, ambient temperature in celsius
     */
    private void updateTemperature(float temperature) {
        temperatures.add(temperature);
        textTemp.setText(String.format("%.1f", temperature));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Toast.makeText(getApplicationContext(), "GPS Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Toast.makeText(getApplicationContext(), "GPS Disabled", Toast.LENGTH_LONG).show();
        // Notify user to turn on.
        AlertDialog.Builder dialog = new AlertDialog.Builder(NavigateActivity.this)
                .setTitle("GPS Disabled")
                .setMessage(R.string.gps_disable_dialog_msg)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "dialog [Yes] clicked");
                    }
                });
        dialog.show();
    }


    /**
     * Convey [Back] key click to onButtonStop()
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Disable back key
        if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_DOWN)) {
            onButtonStop();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

}

/**
 * Rewards of finishing activity
 * Determined by average speed(s), distance(d), average temperature(t).
 */
enum Reward {
    Peach {
        public String getPureName() {
            return "Peach";
        }

        public String getName() {
            return getPureName() + "🍑";
        }
    },  // 4 ≤ s < 6, d ≤ 1, 4 < t < 20°
    Watermelon {
        public String getPureName() {
            return "Watermelon";
        }

        public String getName() {
            return getPureName() + "🍉";
        }
    }, // 4 ≤ s < 6, d > 1, t ≥ 20°
    IceCream {
        public String getPureName() {
            return "Ice Cream";
        }

        public String getName() {
            return getPureName() + "🍦";
        }
    },   //6 ≤ s < 8, d > 0, t ≥ 20°
    Banana {
        public String getPureName() {
            return "Banana";
        }

        public String getName() {
            return getPureName() + "🍌";
        }
    }, // s ≥ 8, d > 1, 4 < t < 20°
    Apple {
        public String getPureName() {
            return "Apple";
        }

        public String getName() {
            return getPureName() + "🍎";
        }
    };  // All other cases

    public abstract String getName();

    public abstract String getPureName();
}