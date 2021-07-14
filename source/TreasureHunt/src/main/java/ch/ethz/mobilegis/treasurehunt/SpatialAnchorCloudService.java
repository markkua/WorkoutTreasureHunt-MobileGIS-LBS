package ch.ethz.mobilegis.treasurehunt;

import android.app.Application;
import android.os.Bundle;

import com.microsoft.CloudServices;

public class SpatialAnchorCloudService extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Use application's context to initialize CloudServices!
        CloudServices.initialize(this);
    }
}