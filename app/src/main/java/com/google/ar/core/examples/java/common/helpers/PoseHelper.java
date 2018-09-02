package com.google.ar.core.examples.java.common.helpers;

import com.google.ar.core.Pose;

public class PoseHelper {
    /**
     * Calculate the distance between two poses
     * @param a First pose
     * @param b Second pose
     * @return The distance in the same units as the input poses
     */
    public static double distance(Pose a, Pose b) {
        float x = a.tx() - b.tx();
        float y = a.ty() - b.ty();
        float z = a.tz() - b.tz();
        return Math.sqrt(x * x + y * y + z * z);
    }
}
