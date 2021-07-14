package ch.ethz.mobilegis.treasurehunt;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import android.util.Log;

import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.AnchorLocatedEvent;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorSession;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorWatcher;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;
import com.microsoft.azure.spatialanchors.LocateAnchorsCompletedEvent;
import com.microsoft.azure.spatialanchors.SessionLogLevel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.ar.sceneform.rendering.ModelRenderable;
import com.microsoft.azure.spatialanchors.SessionUpdatedEvent;

import java.lang.ref.WeakReference;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;

/**
 * ARActivity.java
 *
 * Author: Bingxin Ke
 * Last edited: 2021-06-07
 *
 * Tested on:  <Google Pixel 3> with Android 10 (HAVE NOT tested on emulator)
 *
 * Work together with "RewardAnchor"
 *
 * Description:
 *      This is the assignment 3 of Mobile GIS and Location-Based Services FS2021, ETH
 *      I mainly implement these functionalities:
 *      0. User guide in each step (bottom white text)
 *      1. Enable Google AR
 *      2. Load rewards from ArcGIS Layers
 *      3. Load .gltf models and corresponding title label
 *      4. Place and render models in AR view (unique model for each reward)
 *      5. Save model anchors locally and upload to Azure cloud
 *      6. Locate model anchors, using anchorId(saved locally) and Azure anchor cloud service
 *
 * Config:
 *      1. set <USERID> in this file (my userid is 5, recommend to use 11 for all rewards)
 *      2. setup Azure key in <strings.xml>
 *          i. <acountID>
 *          ii. <accountKey>
 *          iii. <accountDomain>
 *      3. setup ArcGIS key in build.gradle(app)
 *
 * */

public class ARActivity extends AppCompatActivity {
    private static final String TAG = ARActivity.class.getSimpleName();
    private static final String USERID = "11";  // = "5"
    private static final String UPLOADING_ANCHOR_KEY = "uploading";
    private static String ANCHOR_FILENAME;

    // UI Components
    private ArFragment arFragment;
    private TextView scanProgressText;
    private TextView statusText;
    private Button actionButton;
    private Button backButton;
    private Button restartButton;
    private Spinner spinnerReward;
    private HashMap<String, Integer> rewardCount = new HashMap<>();  // e.g. <"apple", 2>

    // UI flow control
    private DemoStep currentDemoStep = DemoStep.Start;
    private boolean rewardToPlace = true;
    private boolean mUserRequestedInstall = true;  // boolean for checking if Google Play Services for AR if necessary.
    private boolean enoughDataForSaving = false;
    private boolean modelsLoaded = false;
    private boolean loadFromFile = false;
    private final Object progressLock = new Object();
    private final Object renderLock = new Object();
    private final Object modelLoadLock = new Object();
    private final Object tapLock = new Object();
    private boolean tapEnabled = true;
    private String selectedReward;
    private int lastSpinnerPosition;
    private ArrayList<String> spinnerStr = new ArrayList<>();

    // Spatial Anchor
    private ConcurrentHashMap<String, Renderable> models = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Float> modelScale = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ViewRenderable> viewRenderables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RewardAnchor> rewardAnchors = new ConcurrentHashMap<>();
    private float recommendedSessionProgress = 0f;
    private CloudSpatialAnchorSession cloudSession;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private int saveCount = 0;
    // For locating
    private ArrayList<String> savedAnchorId = new ArrayList<>();
    private HashMap<String, String> anchorRewardType = new HashMap<>();  // <anchorId, rewardType>
    private HashMap<String, Integer> anchorRewardNum = new HashMap<>();  // <anchorId, number>

    // AR
    private ArSceneView sceneView;
    private boolean sessionInited = false;

    // ArcGIS
    private ServiceFeatureTable mServiceFeatureTable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aractivity);

        ANCHOR_FILENAME = getResources().getString(R.string.anchor_file_name);

        // UI Components
        statusText = findViewById(R.id.statusText);
        scanProgressText = findViewById(R.id.scanProgressText);
        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(this::onBackButtonClicked);
        actionButton = findViewById(R.id.actionButton);
        actionButton.setOnClickListener((View v) -> onActionButtonClicked());
        restartButton = findViewById(R.id.restartButton);
        restartButton.setOnClickListener((View v) -> onRestartButtonClicked());

        spinnerReward = findViewById(R.id.spinnerReward);

        // Enable AR-related functionality on ARCore supported devices only.
        checkARCoreSupported();

        // Setting the on Tap Listener to the fragment
        this.arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        this.arFragment.setOnTapArPlaneListener(this::onTapArPlaneListener);

        // create the service feature table
        mServiceFeatureTable = new ServiceFeatureTable(getResources().getString(R.string.round_trip_layer_url));

        initSpinnerReward();

        runOnUiThread(() -> {
            restartButton.setEnabled(true);
            actionButton.setVisibility(View.INVISIBLE);
        });

        // Load local models and corresponding scale
        loadModels();
        setModelScale();

        currentDemoStep = DemoStep.Start;
        Log.d(TAG, "currentDemoStep: " + currentDemoStep);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume()");
        super.onResume();

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        try {
            switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                case INSTALL_REQUESTED:
                    mUserRequestedInstall = false;
                    return;
            }
        } catch (UnavailableUserDeclinedInstallationException | UnavailableDeviceNotCompatibleException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "Exception creating session: " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        }

        //  Check local anchor.csv
        Log.d(TAG, "file exist: " + checkAnchorFileExist(ANCHOR_FILENAME));
        if (checkAnchorFileExist(ANCHOR_FILENAME)) {
            // if exist local anchor file, enter Locate steps
            loadFromFile = true;

            currentDemoStep = DemoStep.BeforeLocate;
            Log.d(TAG, "currentDemoStep: " + currentDemoStep);
        }

        if (DemoStep.Start == currentDemoStep) {
            startDemo();
        } else if (DemoStep.BeforeLocate == currentDemoStep) {
            runOnUiThread(() -> {
                statusText.setText("");
                spinnerReward.setEnabled(false);
                actionButton.setText("Load last anchors from cloud");
                actionButton.setVisibility(View.VISIBLE);
            });
        }
    }

    /* ************************* Read and load local anchor file ************************* */
    /**
     * Save all located anchors to a csv file:
     *      anchor_id,reward_name,reward_number
     *
     * @param filename: inner storage filename (without path)
     */
    private void saveAnchorToFile(String filename) {
        try {
            FileOutputStream fos = openFileOutput(filename, MODE_PRIVATE);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "utf8");
            BufferedWriter bufferedWriter = new BufferedWriter(osw);
            int count = 0;
            for (RewardAnchor rewardAnchor : rewardAnchors.values()) {
                if (0 != count) {
                    bufferedWriter.newLine();
                }
                String anchorId = rewardAnchor.getAnchorId();
                String reward = rewardAnchor.getReward();
                String number = Integer.toString(rewardAnchor.getNumber());
                String line = anchorId + "," + reward + "," + number;
                bufferedWriter.write(line);
                count++;
            }
            bufferedWriter.flush();
            bufferedWriter.close();
            osw.close();
            fos.close();
            Log.i(TAG, Integer.toString(count) + " anchors saved to file " + filename);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Save local file fail, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (IOException e) {
            Toast.makeText(this, "Save local file fail, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * Read csv file and save anchor information.
     * Format of csv file:
     *      anchor_id,reward_name,reward_number
     *
     * @param filename: inner storage filename (without path)
     */
    private void readAnchorFromFile(String filename) {
        try {
            FileInputStream fis = openFileInput(filename);
            InputStreamReader isr = new InputStreamReader(fis, "utf8");
            BufferedReader bufferedReader = new BufferedReader(isr);

            savedAnchorId.clear();
            anchorRewardType.clear();
            anchorRewardNum.clear();

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] lineSplit = line.split(",");
                Log.d(TAG, Arrays.toString(lineSplit));
                String anchorId = lineSplit[0];
                String reward = lineSplit[1];
                int number = Integer.parseInt(lineSplit[2]);
                savedAnchorId.add(anchorId);
                anchorRewardType.put(anchorId, reward);
                anchorRewardNum.put(anchorId, number);
            }

            bufferedReader.close();
            isr.close();
            fis.close();

        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Read local file fail, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (IOException e) {
            Toast.makeText(this, "Read local file fail, please check permission and retry.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * Check whether a file is exist.
     * If no anchor csv file exist, start placing session.
     * Else, load anchor ID from file and load anchors from cloud
     *
     * @param filename: inner storage filename (without path)
     */
    private boolean checkAnchorFileExist(String filename) {
        String path = getFilesDir().getAbsolutePath() + "/" + filename;
        File file = new File(path);
        return file.exists();
    }

    /**
     * Delete file in inner storage.
     * When use clean up all anchors, delete anchor csv as well.
     * Called in "onActionButtonClicked()"
     *
     * @param filename: inner storage filename (without path)
     */
    private boolean deleteAnchorFile(String filename) {
        String path = getFilesDir().getAbsolutePath() + "/" + filename;
        File file = new File(path);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }
    /* ************************* *************************** ************************* */


    /* ******************************** UI flow control ****************************** */

    /**
     * Starting step, initialize some UI components and UI flow control variables
     */
    private void startDemo() {
        Log.i(TAG, "startDemo()");
        saveCount = 0;

        // reset UI
        tapEnabled = true;
        rewardToPlace = true;
        enoughDataForSaving = false;

        runOnUiThread(() -> {
            actionButton.setVisibility(View.INVISIBLE);
            backButton.setEnabled(true);
            restartButton.setVisibility(View.VISIBLE);
            restartButton.setEnabled(false);
            spinnerReward.setEnabled(true);

            scanProgressText.setVisibility(View.GONE);
            statusText.setText("Tap a surface to create an anchor");
            actionButton.setVisibility(View.INVISIBLE);
        });
        currentDemoStep = DemoStep.CreateLocalAnchor;
        Log.d(TAG, "currentDemoStep: " + currentDemoStep);
    }

    /**
     * When user click Restart button, call up onActionButtonClicked() case Restart
     */
    private void onRestartButtonClicked() {
        Log.i(TAG, "onRestartButtonClicked()");
        currentDemoStep = DemoStep.Restart;
        Log.d(TAG, "currentDemoStep: " + currentDemoStep);
        onActionButtonClicked();
    }

    /**
     * Go back to main activity
     * @param v: pass from button click
     */
    public void onBackButtonClicked(View v) {
        Log.i(TAG, "onBackButtonClicked()");
        synchronized (renderLock) {
            // destroy session
            stopSession();
            finish();
        }
    }

    /**
     * Important function. Control most UI flows step by step
     */
    private void onActionButtonClicked() {
        Log.i(TAG, "onActionButtonClicked(), currentDemoStep: " + currentDemoStep);
        switch (currentDemoStep) {
            case SaveCloudAnchor:
                if (!enoughDataForSaving) {
                    return;
                }

                // Hide the back button until we're done
                runOnUiThread(() -> {
                    backButton.setEnabled(false);
                    restartButton.setEnabled(false);
                });

                // Get newly created rewardAnchor
                RewardAnchor rewardAnchor = rewardAnchors.get(UPLOADING_ANCHOR_KEY);

                assert rewardAnchor != null;
                if (!enoughDataForSaving) {
                    Log.e(TAG, "ERROR: save when not enoughDataForSaving");
                }

                // Add cloud anchor
                // set expire automatically
                Date now = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.DATE, 7);
                Date oneWeekFromNow = cal.getTime();
                rewardAnchor.addCloudAnchor(oneWeekFromNow);

                // Upload cloud anchor async
                uploadCloudAnchorAsync(rewardAnchor.getCloudAnchor())
                        .thenAccept(this::onAnchorSaveSuccess).exceptionally(thrown -> {
                    Log.e(TAG, "uploadCloudAnchorAsync Error");
                    return null;
                });

                synchronized (progressLock) {
                    runOnUiThread(() -> {
                        scanProgressText.setVisibility(View.GONE);
                        scanProgressText.setText("");
                        actionButton.setVisibility(View.INVISIBLE);
                        statusText.setText("Saving cloud anchor...");
                    });
                    currentDemoStep = DemoStep.SavingCloudAnchor;
                    Log.d(TAG, "currentDemoStep: " + currentDemoStep);
                }
                break;

            case BeforeLocate:
                // Prepare for locating
                runOnUiThread(() -> spinnerReward.setEnabled(false));
                prepareForLocate();

                startSession();

                break;

            case LookForAnchor:
                // Locate anchors
                AnchorLocateCriteria criteria = new AnchorLocateCriteria();
                criteria.setIdentifiers(this.savedAnchorId.toArray(new String[0]));
                Log.d(TAG, "this.savedAnchorId.toArray(new String[0]): " + Arrays.toString(this.savedAnchorId.toArray(new String[0])));

                // Cannot run more than one watcher concurrently
                stopLocating();

                // Start locating
                this.cloudSession.createWatcher(criteria);

                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    statusText.setText("Look for anchor");
                });
                break;

            case Restart:
                // Clear all and restart
                clearLocalAnchors();
                deleteAnchorFile(ANCHOR_FILENAME);

                initSpinnerReward();

                startSession();

                this.savedAnchorId.clear();
                this.anchorRewardType.clear();
                this.anchorRewardNum.clear();

                startDemo();

                break;
        }
    }

    /**
     * Called when an anchor is saved to cloud.
     * Do the following:
     *      Save anchor information into file
     *      Update UI
     *      Remove corresponding reward from spinner
     *      Decide continue placing or end placing
     * @param id: anchor id of saved anchor
     */
    private void onAnchorSaveSuccess(String id) {
        saveCount++;
        Log.d(TAG, "saveCount: " + saveCount);
        Log.i(TAG, String.format("Cloud Anchor created: %s", id));

        // Update key of anchor
        RewardAnchor rewardAnchor = rewardAnchors.get(UPLOADING_ANCHOR_KEY);
        assert rewardAnchor != null;
        rewardAnchor.setAnchorId(id);
        rewardAnchors.put(selectedReward, rewardAnchor);
        rewardAnchors.remove(UPLOADING_ANCHOR_KEY);

        // Save to file
        saveAnchorToFile(ANCHOR_FILENAME);

        runOnUiThread(() -> {
            statusText.setText("Saved.");

            backButton.setEnabled(true);
            restartButton.setEnabled(true);
            spinnerReward.setEnabled(true);

            // remove last selected from spinner
            spinnerStr.remove(lastSpinnerPosition);
            setSpinnerAdapter();
            if (spinnerStr.stream().count() > 0) {
                spinnerReward.setSelection(0);
            } else {
                rewardToPlace = false;
                Log.d(TAG, "rewardToPlace: " + rewardToPlace);
            }

            if (rewardToPlace) {
                // continue Place
                tapEnabled = true;
                Log.d(TAG, "tapEnabled: " + tapEnabled);

                statusText.setText("Tap a surface to create next anchor");
                actionButton.setVisibility(View.INVISIBLE);

                currentDemoStep = DemoStep.CreateLocalAnchor;
                Log.d(TAG, "currentDemoStep: " + currentDemoStep);
            } else {
                // Place finished
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setEnabled(false);

                currentDemoStep = DemoStep.BeforeLocate;
                Log.d(TAG, "currentDemoStep: " + currentDemoStep);
                statusText.setText("Placed all reward(s)");
                actionButton.setText("Clear local anchors");
                actionButton.setEnabled(true);
            }
        });
    }

    /**
     * Listen to tap on plane in AR view.
     * Call functions to create local anchor
     *
     * @param hitResult: hit result containing location
     * @param plane: passed by AR core
     * @param motionEvent: passed by AR core
     */
    protected void onTapArPlaneListener(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        Log.i(TAG, "onTapArPlaneListener()");
        synchronized (this.tapLock) {
            if (!tapEnabled) {
                return;
            }
            tapEnabled = false;
            Log.d(TAG, "tapEnabled: " + tapEnabled);
        }

        // only valid when first called
        startSession();

        if (modelsLoaded) {
            if (currentDemoStep == DemoStep.CreateLocalAnchor) {
                // Disable spinner until uploaded
                runOnUiThread(() -> {
                    backButton.setEnabled(false);
                    spinnerReward.setEnabled(false);
                });

                createLocalAnchor(hitResult);
            }
        } else {
            Toast.makeText(this, "Model not loaded, please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Create local anchors, render in AR, and save into a HashMap
     *
     * @param hitResult: hit result containing anchor information
     */
    private void createLocalAnchor(HitResult hitResult) {
        Log.i(TAG, "createLocalAnchor()");
        int number = rewardCount.get(selectedReward);
        Log.d(TAG, "number: " + number);
        RewardAnchor rewardAnchor = new RewardAnchor(selectedReward, number, arFragment, hitResult.createAnchor());

        // Draw a 3D object with glFT file type
        Renderable gltfModel = this.models.get(selectedReward);
        float scale = this.modelScale.get(selectedReward);
        ViewRenderable viewRenderable = this.viewRenderables.get(selectedReward);
        rewardAnchor.setModel(gltfModel, scale);
        rewardAnchor.setViewRenderable(viewRenderable);
        rewardAnchor.render(arFragment);

        // Save into HashMap
        this.rewardAnchors.put(UPLOADING_ANCHOR_KEY, rewardAnchor);

        runOnUiThread(() -> {
            scanProgressText.setVisibility(View.VISIBLE);
            // already enough data (usually not the first anchor)
            if (enoughDataForSaving) {
                statusText.setText("Ready to save");
                actionButton.setText("Save cloud anchor");
                actionButton.setVisibility(View.VISIBLE);
            } else {
                statusText.setText("Move around the anchor");
            }
        });

        currentDemoStep = DemoStep.SaveCloudAnchor;
        Log.d(TAG, "currentDemoStep: " + currentDemoStep);
    }

    /**
     * Prepare for locate cloud anchors. Start new session, prepare anchor IDs.
     */
    private void prepareForLocate() {
        Log.d(TAG, "prepareForLocate()");

        // prepare anchor ID and reward type
        Log.d(TAG, "loadFromFile: " + loadFromFile);
        if (!loadFromFile) {
            savedAnchorId.clear();
            anchorRewardType.clear();
            anchorRewardNum.clear();
            for (Map.Entry<String, RewardAnchor> entry : this.rewardAnchors.entrySet()) {
                RewardAnchor rewardAnchor = entry.getValue();
                savedAnchorId.add(rewardAnchor.getAnchorId());
                anchorRewardType.put(rewardAnchor.getAnchorId(), rewardAnchor.getReward());
                anchorRewardNum.put(rewardAnchor.getAnchorId(), rewardAnchor.getNumber());
            }
        } else {
            readAnchorFromFile(ANCHOR_FILENAME);
            runOnUiThread(() -> {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText("Anchor id loaded from local file");
            });
        }
        Log.d(TAG, "savedAnchorId: " + savedAnchorId.toString());

        onCreateSessionForQuery();

        clearLocalAnchors();

        runOnUiThread(() -> {
            spinnerReward.setEnabled(false);
            statusText.setText("");
            actionButton.setText("Start locating");
        });

        currentDemoStep = DemoStep.LookForAnchor;
        Log.d(TAG, "currentDemoStep: " + currentDemoStep);
    }

    /**
     * Clear local anchors (in app buffer), NOT including anchor file
     */
    private void clearLocalAnchors() {
        for (RewardAnchor rewardAnchor : this.rewardAnchors.values()) {
            rewardAnchor.destroy();
        }

        this.rewardAnchors.clear();
        this.saveCount = 0;
    }
    /* **************************************************************************************** */


    /* ************************* CloudSpatialAnchorSession management ************************* */
    /**
     * Start CloudSpatialAnchorSession
     */
    private void startSession() {
        // Start session
        if (!sessionInited) {
            this.sceneView = arFragment.getArSceneView();
            Scene scene = arFragment.getArSceneView().getScene();
            scene.addOnUpdateListener(frameTime -> {
                if (this.cloudSession != null) {
                    this.cloudSession.processFrame(sceneView.getArFrame());
                }
            });
            initializeSession();
        }
    }

    /**
     * Called by startSession(). Start sessions, setup Azure accounts
     */
    private void initializeSession() {
        if (this.cloudSession != null) {
            this.cloudSession.close();
        }

        this.cloudSession = new CloudSpatialAnchorSession();
        this.cloudSession.setSession(sceneView.getSession());
        this.cloudSession.setLogLevel(SessionLogLevel.Information);
        this.cloudSession.addOnLogDebugListener(args -> Log.d(TAG, args.getMessage()));
        this.cloudSession.addErrorListener(args -> Log.e("ASAError", String.format("%s: %s",
                args.getErrorCode().name(), args.getErrorMessage())));

        this.cloudSession.addSessionUpdatedListener(this::onSessionUpdated);
        this.cloudSession.addAnchorLocatedListener(this::onAnchorLocated);
        this.cloudSession.addLocateAnchorsCompletedListener(this::onLocateAnchorsCompleted);

        this.cloudSession.getConfiguration().setAccountId(getString(R.string.accountID));
        this.cloudSession.getConfiguration().setAccountKey(getString(R.string.accountKey));
        this.cloudSession.getConfiguration().setAccountDomain(getString(R.string.accountDomain));
        this.cloudSession.start();

        sessionInited = true;
    }

    /**
     * Stop CloudSpatialAnchorSession
     */
    private void stopSession() {
        Log.d(TAG, "stopSession()");
        if (sessionInited) {
            cloudSession.stop();
            sessionInited = false;
        }
        stopLocating();
    }

    /**
     * reset CloudSpatialAnchorSession
     */
    private void resetSession() {
        Log.d(TAG, "resetSession()");
        if (sessionInited) {
            stopLocating();
            cloudSession.reset();
        }
    }

    /**
     * Stoop locating procedural
     */
    public void stopLocating() {
        Log.d(TAG, "stopLocating()");
        if (sessionInited) {

            List<CloudSpatialAnchorWatcher> watchers = cloudSession.getActiveWatchers();

            if (watchers.isEmpty()) {
                return;
            }

            // Only 1 watcher is at a time is currently permitted.
            CloudSpatialAnchorWatcher watcher = watchers.get(0);

            watcher.stop();
        }
    }

    /**
     * Reset CloudSpatialAnchorSession for locating. Called in prepareForLocate()
     */
    private void onCreateSessionForQuery() {
        Log.d(TAG, "onCreateSessionForQuery()");
        stopSession();
        resetSession();
    }
    /* ********************************************************************************* */



    /* **************************** Locate and render anchors ************************** */

    /**
     * When a cloud anchor is located, call this function to process.
     *
     * @param event: anchor is contained in this event
     */
    private void onAnchorLocated(AnchorLocatedEvent event) {
        Log.d(TAG, "onAnchorLocated: " + event.toString());
        LocateAnchorStatus status = event.getStatus();

        runOnUiThread(() -> {
            switch (status) {
                case AlreadyTracked:
                    break;

                case Located:
                    renderLocatedAnchor(event.getAnchor());
                    break;

                case NotLocatedAnchorDoesNotExist:
                    statusText.setText("Anchor does not exist");
                    break;
            }
        });
    }

    /**
     * When all cloud anchors are located, call this function. Continue to next step.
     *
     * @param event: not used
     */
    private void onLocateAnchorsCompleted(LocateAnchorsCompletedEvent event) {
        runOnUiThread(() -> statusText.setText("Anchor located!"));

        stopLocating();
        runOnUiThread(() -> {
            restartButton.setEnabled(true);
        });
        currentDemoStep = DemoStep.Restart;
        Log.d(TAG, "currentDemoStep: " + currentDemoStep);
    }

    /**
     * Create and render a located cloud anchor. Save the anchor into HashMap.
     * Called by onAnchorLocated()
     *
     * @param anchor
     */
    private void renderLocatedAnchor(CloudSpatialAnchor anchor) {
        String id = anchor.getIdentifier();
        String reward = anchorRewardType.get(id);
        int number = anchorRewardNum.get(id);
        Log.i(TAG, "renderLocatedAnchor: id=" + id + ", reward=" + reward);

        RewardAnchor rewardAnchor = new RewardAnchor(reward, number, arFragment, anchor.getLocalAnchor());
        rewardAnchor.setCloudAnchor(anchor);
        rewardAnchor.getAnchorNode().setParent(arFragment.getArSceneView().getScene());

        rewardAnchor.setModel(models.get(reward), modelScale.get(reward));
        rewardAnchor.setViewRenderable(viewRenderables.get(reward));
        rewardAnchor.render(arFragment);
        rewardAnchors.put(reward, rewardAnchor);
    }
    /* ********************************************************************************* */



    /* ***************************** Scan and update anchors *************************** */

    /**
     * Listen to session process, update process when placing anchors
     *
     * @param args
     */
    private void onSessionUpdated(SessionUpdatedEvent args) {
        synchronized (this.progressLock) {
            // Update progress
            this.recommendedSessionProgress = args.getStatus().getRecommendedForCreateProgress();
            Log.i(TAG, String.format("Session progress: %f", this.recommendedSessionProgress));
            this.enoughDataForSaving = this.recommendedSessionProgress > 1;
            Log.i(TAG, "enoughDataForSaving: " + enoughDataForSaving);

            if (currentDemoStep == DemoStep.SaveCloudAnchor) {
                // Scanning progress
                DecimalFormat decimalFormat = new DecimalFormat("00");
                runOnUiThread(() -> {
                    String progressMessage = "Scan progress: " + decimalFormat.format(Math.min(1.0f, this.recommendedSessionProgress) * 100) + "%";
                    scanProgressText.setText(progressMessage);
                });

                // Scan finished, ready to save
                if (enoughDataForSaving && actionButton.getVisibility() != View.VISIBLE) {
                    // Enable the save button
                    runOnUiThread(() -> {
                        statusText.setText("Ready to save");
                        actionButton.setText("Save cloud anchor");
                        actionButton.setVisibility(View.VISIBLE);
                    });
                    currentDemoStep = DemoStep.SaveCloudAnchor;
                }
            }
        }
    }

    /**
     * Function for uploading to cloud. As soon as enough frames are collected from your device, it will switch the color of the sphere to yellow,
     * and then it will start uploading your local Azure Spatial Anchor into the cloud. Once the upload finishes, the code will return an anchor identifier.
     *
     * @param anchor: anchor to upload
     * @return
     */
    private CompletableFuture<String> uploadCloudAnchorAsync(CloudSpatialAnchor anchor) {
        synchronized (this.progressLock) {
            this.enoughDataForSaving = false;
        }

        return CompletableFuture.runAsync(() -> {
            try {
                float currentSessionProgress;
                do {
                    synchronized (this.progressLock) {
                        currentSessionProgress = this.recommendedSessionProgress;
                    }
                    if (currentSessionProgress < 1.0) {
                        Thread.sleep(500);
                    }
                }
                while (currentSessionProgress < 1.0);

                // Scan finished
                synchronized (this.progressLock) {
                    enoughDataForSaving = true;
                    Log.i(TAG, "enoughDataForSaving: " + enoughDataForSaving);
                }

                this.cloudSession.createAnchorAsync(anchor).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e("ASAError", e.toString());
                throw new RuntimeException(e);
            }
        }, executorService).thenApply(ignore -> anchor.getIdentifier());
    }
    /* ******************************************************************************* */


    /* *********************************** Preparation ******************************* */

    /**
     * Check AR Core support, if not installed, call up Google Play Store to install
     */
    void checkARCoreSupported() {
        Log.i(TAG, "checkARCoreSupported()");
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Continue to query availability at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkARCoreSupported();
                }
            }, 200);
        }
    }

    /**
     * Load local gltf models and save into a HashMap
     *
     */
    public void loadModels() {
        this.models.clear();
        for (Reward reward : Reward.values()) {
            load1Model(reward.getPureName());
            load1View(reward.getPureName());
        }
    }

    /**
     * A function to load 1 3D model from gltf
     *
     * @param reward: reward name (Apple, Banana ...)
     */
    private void load1Model(String reward) {
        WeakReference<ARActivity> weakActivity = new WeakReference<>(this);

        ModelRenderable.builder()
                .setSource(this, Uri.parse("models/" + reward.replace(' ', '_') + ".gltf"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    ARActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.models.put(reward, model);
                        Log.i(TAG, "Model loaded: " + reward);
                        checkModelLoad();
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Unable to load model: " + throwable.toString());
                    return null;
                });
    }

    /**
     * Load 1 TextView layout to render as a label in AR
     *
     * @param reward: reward name (Apple, Banana ...)
     */
    private void load1View(String reward) {
        WeakReference<ARActivity> weakActivity = new WeakReference<>(this);

        ViewRenderable.builder()
                .setView(this, R.layout.view_card)
                .build()
                .thenAccept(viewRenderable -> {
                    ARActivity activity = weakActivity.get();
                    if (activity != null) {
                        Log.i(TAG, "viewRenderable loaded: " + reward);
                        activity.viewRenderables.put(reward, viewRenderable);
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Unable to load model: " + throwable.toString());
                    return null;
                });
    }

    /**
     * Check if all models are loaded
     */
    private void checkModelLoad() {
        if (models.mappingCount() == Arrays.stream(Reward.values()).count()) {
            synchronized (modelLoadLock) {
                this.modelsLoaded = true;
                for (Reward reward : Reward.values()) {
                    if (!models.containsKey(reward.getPureName()) | null == models.get(reward.getPureName())) {
                        this.modelsLoaded = false;
                        break;
                    }
                }
                Toast.makeText(this, "Models load" + (this.modelsLoaded ? "ed" : " fail"), Toast.LENGTH_SHORT).show();
                Log.i(TAG, "modelsLoaded: " + modelsLoaded);
            }
        }
    }

    /**
     * Setup different scales for models
     */
    private void setModelScale() {
        for (Reward reward : Reward.values()) {
            switch (reward) {
                case Apple:
                    modelScale.put(Reward.Apple.getPureName(), 0.0009f);
                    break;
                case Banana:
                    modelScale.put(Reward.Banana.getPureName(), 0.0025f);
                    break;
                case IceCream:
                    modelScale.put(Reward.IceCream.getPureName(), 0.025f);
                    break;
                case Peach:
                    modelScale.put(Reward.Peach.getPureName(), 0.015f);
                    break;
                case Watermelon:
                    modelScale.put(Reward.Watermelon.getPureName(), 0.5f);
                    break;
            }
        }
    }

    /**
     * Load rewards from ArcGIS Layer, and initialize the spinner
     */
    private void initSpinnerReward() {
        Log.d(TAG, "initSpinnerReward()");

        //create query parameters
        QueryParameters queryParams = new QueryParameters();
        queryParams.setWhereClause("user_id=" + USERID);
        // QueryFeatureFields to get all features
        ServiceFeatureTable.QueryFeatureFields queryFeatureFields = ServiceFeatureTable.QueryFeatureFields.LOAD_ALL;
        //query feature from the table
        final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(queryParams, queryFeatureFields);
        // add done loading listener to fire when the selection returns
        future.addDoneListener(() -> {
            // Load rewards
            try {
                // call get on the future to get the result
                FeatureQueryResult result = future.get();

                // count reward number
                Iterator<Feature> resultIterator = result.iterator();
                rewardCount.clear();
                for (Reward reward : Reward.values()) {
                    rewardCount.put(reward.getPureName(), 0);
                }

                while (resultIterator.hasNext()) {
                    // get the extent of the first feature in the result to zoom to
                    Feature feature = resultIterator.next();
                    Map<String, Object> attr = feature.getAttributes();
                    String reward = (String) attr.get("reward");
                    try {
                        if (null == reward) {
                            throw new NullPointerException("invalid reward");
                        }
                        rewardCount.replace(reward, rewardCount.get(reward) + 1);
                    } catch (NullPointerException e) {
                        Log.e(TAG, "Invalid reward name in Feature Layers");
                        Toast.makeText(getApplicationContext(),
                                "Invalid reward name in Feature Layers. Please contact admin.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                Toast.makeText(this, "Rewards loaded for user: " + USERID, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Fail to load rewards, please retry.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "error: " + e.toString());
            }
            // prepare numbers
            spinnerStr.clear();
            for (Map.Entry<String, Integer> entry : rewardCount.entrySet()) {
                String rewardName = entry.getKey();
                int num = entry.getValue();
                spinnerStr.add(rewardName + "(" + num + ")");
            }
            spinnerStr.sort(null);
            // Setup spinner
            setSpinnerAdapter();

            // Set spinner listener
            spinnerReward.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    lastSpinnerPosition = position;
                    onRewardSelected();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        });
    }

    /**
     * Setup spinner adapter. Called when spinner initialized and content updated
     */
    private void setSpinnerAdapter() {
        SpinnerAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerStr);
        spinnerReward.setAdapter(adapter);
    }

    /**
     * A listen to spinner selected action.
     */
    private void onRewardSelected() {
        this.selectedReward = spinnerReward.getSelectedItem().toString().split("\\(")[0];
        Log.i(TAG, "onRewardSelected(), selected: " + selectedReward);
    }
    /* ******************************************************************************* */


    /**
     * Enum for UI flow control.
     * */
    enum DemoStep {
        Start,                  ///< the start of the demo
        CreateLocalAnchor,      ///< the session will create a local anchor
        SaveCloudAnchor,        ///< the session will save the cloud anchor
        SavingCloudAnchor,      ///< the session is in the process of saving the cloud anchor
        BeforeLocate,           ///< All anchors are placed, wait for locate
        LookForAnchor,          ///< the session will run the query
        Restart,                ///< waiting to restart
    }
}

