package ch.ethz.mobilegis.treasurehunt;

import android.location.Location;
import com.esri.arcgisruntime.geometry.Point;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * GameResult.java
 *
 * Author: Bingxin Ke
 * Last edited: 2021-05-06
 *
 * New component to solve Assignment 2 - task 2
 *
 * Data Structures to save game results, incl. track and check point
 *

 * */


class TrackResult implements Serializable {
//    private ArrayList<Location> trackLocation;
    private ArrayList<LonLatPoint> trackPoints;
    private Long startTimestamp;
    private int userId;
    private int trackId;
    private String rewardName;
    private double distance;
    private double duration;
    private double avgSpeed;
    private double avgTemperature;


    public TrackResult(ArrayList<LonLatPoint> trackPoints, Long startTimestamp, int userId,
                       int trackId, String rewardName, double distance, double duration,
                       double avgSpeed, double avgTemperature) {
        this.trackPoints = trackPoints;
        this.startTimestamp = startTimestamp;
        this.userId = userId;
        this.trackId = trackId;
        this.rewardName = rewardName;
        this.distance = distance;
        this.duration = duration;
        this.avgSpeed = avgSpeed;
        this.avgTemperature = avgTemperature;
    }

    public ArrayList<LonLatPoint> getTrackPoints() {
        return trackPoints;
    }

    public void setTrackPoints(ArrayList<LonLatPoint> trackPoints) {
        this.trackPoints = trackPoints;
    }

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTrackId() {
        return trackId;
    }

    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }

    public String getRewardName() {
        return rewardName;
    }

    public void setRewardName(String rewardName) {
        this.rewardName = rewardName;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(double avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public double getAvgTemperature() {
        return avgTemperature;
    }

    public void setAvgTemperature(double avgTemperature) {
        this.avgTemperature = avgTemperature;
    }
}


class PointResult implements Serializable {
    private CheckPoint checkPoint;
    private long arrival_timestamp;
    private int userId;
    private int trackId;


    public PointResult(CheckPoint checkPoint, long arrival_timestamp, int userId, int trackId) {
        this.checkPoint = checkPoint;
        this.arrival_timestamp = arrival_timestamp;
        this.userId = userId;
        this.trackId = trackId;
    }



    public CheckPoint getPoint() {
        return checkPoint;
    }

    public void setPoint(CheckPoint checkPoint) {
        this.checkPoint = checkPoint;
    }

    public long getArrival_timestamp() {
        return arrival_timestamp;
    }

    public void setArrival_timestamp(long arrival_timestamp) {
        this.arrival_timestamp = arrival_timestamp;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTrackId() {
        return trackId;
    }

    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }
}

class LonLatPoint implements Serializable {
    private double longitude;
    private double latitude;

    public LonLatPoint(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

}