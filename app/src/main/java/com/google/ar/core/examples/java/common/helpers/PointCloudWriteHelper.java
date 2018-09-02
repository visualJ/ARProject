package com.google.ar.core.examples.java.common.helpers;

import android.os.Environment;

import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;

public class PointCloudWriteHelper {

    public static final String DIRECTORY = "ARProject";

    /**
     * Write a point cloud to an obj file.
     * @param pointCloud The point cloud to save
     * @param cameraPose The camera pose in local world coordinates this point cloud was captures from
     * @param name the file name without path and extention
     */
    public static void save(PointCloud pointCloud, Pose cameraPose, String name) {
        FloatBuffer points = pointCloud.getPoints();
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        dir = new File(dir, DIRECTORY);
        if (!dir.exists()) {
            dir.mkdir();
        }
        name += ".obj";
        File file = new File(dir, name);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            while (points.remaining() >= 3) {
                float[] point = new float[]{points.get(), points.get(), points.get()};
                points.get(); // ignore the confidence value
                point = cameraPose.inverse().transformPoint(point);
                String line = "v " + point[0] + " " + point[1]+ " " + point[2] + System.lineSeparator();
                fos.write(line.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
