package com.google.ar.core.examples.java.common.helpers;

import android.graphics.Bitmap;
import android.media.Image;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class ImageWriteHelper {

    public static final String DIRECTORY = "ARProject";

    /**
     * Save an image to a file.
     * @param image The image to write
     * @param fileName The file name without extention and path to write to
     */
    public static void saveImage(Image image, String fileName) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            int remaining = buffer.remaining();
            int[] pixels = new int[remaining];
            for (int i = 0; i < remaining; i++) {
                // Alpha
                int pixel = 0xFF;
                // Grayscale
                byte value = buffer.get();
                for (int j = 0; j < 3; j++) {
                    pixel <<= 8;
                    pixel += value;
                }
                pixels[i] = pixel;
            }
            Bitmap bitmap = Bitmap.createBitmap(pixels, image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            dir = new File(dir, DIRECTORY);
            if (!dir.exists()) {
                dir.mkdir();
            }
            fileName += ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, fos);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}
