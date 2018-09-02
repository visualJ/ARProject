package com.google.ar.core.examples.java.cloudanchor;

import com.google.ar.core.Pose;

import java.util.Arrays;
import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;

public class DepthImagePointRepository {

    private Box<DepthImagePoint> box;

    public DepthImagePointRepository(BoxStore boxStore) {
        this.box = boxStore.boxFor(DepthImagePoint.class);
    }

    public List<DepthImagePoint> getDepthImagePoints(Pose anchorPose) {
        List<DepthImagePoint> depthImagePoints = box.getAll();
        for (DepthImagePoint depthImagePoint : depthImagePoints) {
            depthImagePoint.updatePoseRelativeToOrigin(anchorPose);
        }
        return depthImagePoints;
    }

    public List<String> getDepthImagePointNames() {
        return Arrays.asList(box.query().build().property(DepthImagePoint_.name).findStrings());
    }

    public void deleteAllDepthImagePoints() {
        box.removeAll();
    }

    public long putDepthImagePoint(DepthImagePoint point) {
        return box.put(point);
    }

}
