package com.google.ar.core.examples.java.cloudanchor;

import android.support.annotation.NonNull;

import com.google.ar.core.Pose;
import com.google.ar.core.examples.java.common.helpers.PoseHelper;

import java.util.Comparator;

public class DepthImagePointDistanceComparator implements Comparator<DepthImagePoint> {
    private Pose anchorPose = Pose.IDENTITY;

    public Pose getAnchorPose() {
        return anchorPose;
    }

    public void setAnchorPose(@NonNull Pose anchorPose) {
        this.anchorPose = anchorPose;
    }

    @Override
    public int compare(DepthImagePoint a, DepthImagePoint b) {
        double result = PoseHelper.distance(a.getPoseRelativeToOrigin(), anchorPose) - PoseHelper.distance(b.getPoseRelativeToOrigin(), anchorPose);
        return result > 0 ? 1 : result < 0 ? -1 : 0;
    }

}
