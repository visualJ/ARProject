/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * CHANGE NOTICE
 * This file has been modified by Benedikt Ringlein.
 */

package com.google.ar.core.examples.java.cloudanchor;

import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.GuardedBy;
import android.support.constraint.Group;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.CloudAnchorMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FilePermissionHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.ImageWriteHelper;
import com.google.ar.core.examples.java.common.helpers.PointCloudWriteHelper;
import com.google.ar.core.examples.java.common.helpers.PoseHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.objectbox.BoxStore;

public class CloudAnchorActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = CloudAnchorActivity.class.getSimpleName();

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer originRenderer = new ObjectRenderer();
    private final ObjectRenderer anchorRenderer = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    private final ObjectRenderer depthImagePointRenderer = new ObjectRenderer();

    private List<DepthImagePoint> depthImagePoints = new ArrayList<>();
    private BoxStore boxStore;
    private DepthImagePointRepository depthImagePointRepository;
    private Map<String, ObjectRenderer> depthImageRenderers = new HashMap<>();

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];

    // Locks needed for synchronization
    private final Object singleTapLock = new Object();
    private final Object anchorLock = new Object();

    private final SnackbarHelper snackbarHelper = new SnackbarHelper();
    private final CloudAnchorManager cloudManager = new CloudAnchorManager();
    private float modelScaleFactor = 0.1f;
    private boolean showDebugInfo = true;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;
    private boolean installRequested;

    // Tap handling and UI.
    private GestureDetector gestureDetector;
    private DisplayRotationHelper displayRotationHelper;
    private Button hostButton;
    private Button resolveButton;
    private TextView roomCodeText;
    private Button minusButton;
    private Button plusButton;
    private Button xMinus;
    private Button xPlus;
    private Button yMinus;
    private Button yPlus;
    private Button zMinus;
    private Button zPlus;
    private Button saveImagebutton;
    private TextView depthPointLabel;
    private Button deleteDPsButton;
    private Button toggleDebugButton;
    private Button savePointCloudButton;
    private Group debugGroup;

    private DepthImagePointDistanceComparator depthImagePointDistanceComparator = new DepthImagePointDistanceComparator();

    @GuardedBy("singleTapLock")
    private MotionEvent queuedSingleTap;
    private Session session;
    @GuardedBy("anchorLock")
    private Anchor anchor;
    // Cloud Anchor Components.
    private FirebaseManager firebaseManager;
    private HostResolveMode currentMode;
    private RoomCodeAndCloudAnchorIdListener hostListener;

    private Frame frame;
    private Pose leftDepthImagePointPose;
    private Pose middleDepthImagePointPose;

    /**
     * Returns {@code true} if and only if the hit can be used to create an Anchor reliably.
     */
    private static boolean shouldCreateAnchorWithHit(HitResult hit) {
        Trackable trackable = hit.getTrackable();
        if (trackable instanceof Plane) {
            // Check if the hit was within the plane's polygon.
            return ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
        } else if (trackable instanceof Point) {
            // Check if the hit was against an oriented point.
            return ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL;
        }
        return false;
    }

    private void onMinusButtonClick(View view) {
        modelScaleFactor -= 0.05f;
        virtualObject.setScaleFactor(modelScaleFactor);
    }

    private void onPlusButtonClick(View view) {
        modelScaleFactor += 0.05f;
        virtualObject.setScaleFactor(modelScaleFactor);
    }

    private void setShowDebugInfo(boolean showDebugInfo) {
        this.showDebugInfo = showDebugInfo;
        debugGroup.setVisibility(showDebugInfo ? View.VISIBLE : View.GONE);
        for (ObjectRenderer r : depthImageRenderers.values()) {
            r.setOccludeOnly(!showDebugInfo);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);

        if (boxStore == null) {
            boxStore = MyObjectBox.builder().androidContext(getApplicationContext()).build();
        }
        depthImagePointRepository = new DepthImagePointRepository(boxStore);

        // Set up tap listener.
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                synchronized (singleTapLock) {
                                    if (currentMode == HostResolveMode.HOSTING) {
                                        queuedSingleTap = e;
                                    }
                                }
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });
        surfaceView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        installRequested = false;

        virtualObject.setScaleFactor(modelScaleFactor);

        // Initialize UI components.
        hostButton = findViewById(R.id.host_button);
        hostButton.setOnClickListener(this::onHostButtonPress);
        resolveButton = findViewById(R.id.resolve_button);
        resolveButton.setOnLongClickListener(this::onResolveButtonLongPress);
        resolveButton.setOnClickListener(this::onResolveButtonPress);
        roomCodeText = findViewById(R.id.room_code_text);
        minusButton = findViewById(R.id.minus_button);
        minusButton.setOnClickListener(this::onMinusButtonClick);
        plusButton = findViewById(R.id.plus_button);
        plusButton.setOnClickListener(this::onPlusButtonClick);
        xMinus = findViewById(R.id.x_minus);
        xMinus.setOnClickListener(view -> virtualObject.setOffsetX(virtualObject.getOffsetX() - 1));
        xPlus = findViewById(R.id.x_plus);
        xPlus.setOnClickListener(view -> virtualObject.setOffsetX(virtualObject.getOffsetX() + 1));
        yMinus = findViewById(R.id.y_minus);
        yMinus.setOnClickListener(view -> virtualObject.setOffsetY(virtualObject.getOffsetY() - 1));
        yPlus = findViewById(R.id.y_plus);
        yPlus.setOnClickListener(view -> virtualObject.setOffsetY(virtualObject.getOffsetY() + 1));
        zMinus = findViewById(R.id.z_minus);
        zMinus.setOnClickListener(view -> virtualObject.setOffsetZ(virtualObject.getOffsetZ() - 1));
        zPlus = findViewById(R.id.z_plus);
        zPlus.setOnClickListener(view -> virtualObject.setOffsetZ(virtualObject.getOffsetZ() + 1));
        saveImagebutton = findViewById(R.id.save_image_button);
        saveImagebutton.setOnClickListener(this::onImageButtonClicked);
        depthPointLabel = findViewById(R.id.depth_point_label);
        deleteDPsButton = findViewById(R.id.delete_dps_button);
        deleteDPsButton.setOnClickListener(view -> {
            depthImagePointRepository.deleteAllDepthImagePoints();
            depthImagePoints.clear();
            DepthImagePoint.resetNumber();
        });
        toggleDebugButton = findViewById(R.id.toggle_debug_button);
        toggleDebugButton.setOnClickListener(view -> setShowDebugInfo(!showDebugInfo));
        savePointCloudButton = findViewById(R.id.save_point_cloud_button);
        savePointCloudButton.setOnClickListener(view -> {
            PointCloud pointCloud = frame.acquirePointCloud();
            PointCloudWriteHelper.save(pointCloud, frame.getCamera().getPose(), "point_cloud");
            pointCloud.release();
        });
        debugGroup = findViewById(R.id.debug_group);

        // Initialize Cloud Anchor variables.
        firebaseManager = new FirebaseManager(this);
        currentMode = HostResolveMode.NONE;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            int messageId = -1;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }
                if (!FilePermissionHelper.hasFilePermission(this)) {
                    FilePermissionHelper.requestFilePermission(this);
                    return;
                }
                session = new Session(this);
            } catch (UnavailableArcoreNotInstalledException e) {
                messageId = R.string.snackbar_arcore_unavailable;
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                messageId = R.string.snackbar_arcore_too_old;
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                messageId = R.string.snackbar_arcore_sdk_too_old;
                exception = e;
            } catch (Exception e) {
                messageId = R.string.snackbar_arcore_exception;
                exception = e;
            }

            if (exception != null) {
                snackbarHelper.showError(this, getString(messageId));
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            // Create default config and check if supported.
            Config config = new Config(session);
            config.setCloudAnchorMode(CloudAnchorMode.ENABLED);
            session.configure(config);

            // Setting the session in the HostManager.
            cloudManager.setSession(session);
            // Show the inital message only in the first resume.
            snackbarHelper.showMessage(this, getString(R.string.snackbar_initial_message));
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            snackbarHelper.showError(this, getString(R.string.snackbar_camera_unavailable));
            session = null;
            return;
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
        if (!FilePermissionHelper.hasFilePermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!FilePermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                FilePermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    /**
     * Handles the most recent user tap.
     * <p>
     * <p>We only ever handle one tap at a time, since this app only allows for a single anchor.
     *
     * @param frame               the current AR frame
     * @param cameraTrackingState the current camera tracking state
     */
    private void handleTap(Frame frame, TrackingState cameraTrackingState) {
        // Handle taps. Handling only one tap per frame, as taps are usually low frequency
        // compared to frame rate.
        synchronized (singleTapLock) {
            synchronized (anchorLock) {
                // Only handle a tap if the anchor is currently null, the queued tap is non-null and the
                // camera is currently tracking.
                if (anchor == null
                        && queuedSingleTap != null
                        && cameraTrackingState == TrackingState.TRACKING) {
                    Preconditions.checkState(
                            currentMode == HostResolveMode.HOSTING,
                            "We should only be creating an anchor in hosting mode.");
                    for (HitResult hit : frame.hitTest(queuedSingleTap)) {
                        if (shouldCreateAnchorWithHit(hit)) {
                            Anchor newAnchor = hit.createAnchor();
                            Preconditions.checkNotNull(hostListener, "The host listener cannot be null.");
                            cloudManager.hostCloudAnchor(newAnchor, hostListener);
                            setNewAnchor(newAnchor);
                            snackbarHelper.showMessage(this, getString(R.string.snackbar_anchor_placed));
                            break; // Only handle the first valid hit.
                        }
                    }
                }
            }
            queuedSingleTap = null;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(this);
            planeRenderer.createOnGlThread(this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(this);

            virtualObject.createOnGlThread(this, "models/limes_turm.obj", "models/white.png");
            virtualObject.setMaterialProperties(0.0f, 1.5f, 0f, 6.0f);

            originRenderer.createOnGlThread(this, "models/origin.obj", "models/white.png");
            anchorRenderer.createOnGlThread(this, "models/anchor.obj", "models/anchor_texture.png");

            depthImagePointRenderer.createOnGlThread(this, "models/depthimagepoint.obj", "models/white.png");
            depthImagePointRenderer.setMaterialProperties(0.0f, 1.5f, 0f, 6.0f);

            for (String name : depthImagePointRepository.getDepthImagePointNames()) {
                ObjectRenderer renderer = new ObjectRenderer(false);
                renderer.createOnGlThread(this, "depthimages/" + name + ".obj", "models/white.png");
                depthImageRenderers.put(name, renderer);
            }

        } catch (IOException ex) {
            Log.e(TAG, "Failed to read an asset file", ex);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            frame = session.update();
            Camera camera = frame.getCamera();

            Collection<Anchor> updatedAnchors = frame.getUpdatedAnchors();
            TrackingState cameraTrackingState = camera.getTrackingState();

            // Notify the cloudManager of all the updates.
            cloudManager.onUpdate(updatedAnchors);

            // Handle user input.
            handleTap(frame, cameraTrackingState);

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (cameraTrackingState == TrackingState.PAUSED) {
                return;
            }

            // Get camera and projection matrices.
            camera.getViewMatrix(viewMatrix, 0);
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

            // Visualize planes.
            if (showDebugInfo) {
                planeRenderer.drawPlanes(
                        session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);
            }

            // Check if the anchor can be visualized or not, and get its pose if it can be.
            boolean shouldDrawAnchor = false;
            synchronized (anchorLock) {
                if (anchor != null && anchor.getTrackingState() == TrackingState.TRACKING) {
                    // Get the current pose of an Anchor in world space. The Anchor pose is updated
                    // during calls to session.update() as ARCore refines its estimate of the world.
                    // anchor.getPoseRelativeToAnchor().toMatrix(anchorMatrix, 0);
                    virtualObject.setPose(anchor.getPose());
                    anchorRenderer.setPose(anchor.getPose());
                    for (DepthImagePoint p : depthImagePoints) {
                        p.updatePoseRelativeToOrigin(anchor.getPose());
                    }
                    shouldDrawAnchor = true;
                }
            }

            float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // visualize origin
            if (showDebugInfo) {
                originRenderer.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
            }

            // visualize depth image points
            for (DepthImagePoint p : depthImagePoints) {
                depthImagePointRenderer.setPose(p.getPoseRelativeToOrigin());
                if (showDebugInfo) {
                    depthImagePointRenderer.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
                }
                ObjectRenderer depthImageRenderer = depthImageRenderers.get(p.getName());
                if (depthImageRenderer != null) {
                    depthImageRenderer.setPose(p.getPoseRelativeToOrigin());
                    depthImageRenderer.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
                }
            }

            // Visualize anchor and draw the virtual object
            if (shouldDrawAnchor) {
                if (showDebugInfo) {
                    anchorRenderer.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
                }
                virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba);
            }

            // find nearest depth image point
            if (!depthImagePoints.isEmpty()) {
                depthImagePointDistanceComparator.setAnchorPose(frame.getCamera().getPose());
                DepthImagePoint nearestPoint = Collections.min(depthImagePoints, depthImagePointDistanceComparator);
                runOnUiThread(() -> depthPointLabel.setText(nearestPoint.getName()));
            }

            // Visualize tracked points.
            PointCloud pointCloud = frame.acquirePointCloud();
            if (showDebugInfo) {
                pointCloudRenderer.update(pointCloud);
                pointCloudRenderer.draw(viewMatrix, projectionMatrix);
            }

            // if the left part of the stereo image was captured, capture the right part
            // when the camera moved the correct distance
            captureDepthImagePointWithRightDistance(pointCloud);

            pointCloud.release();

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    /**
     * Sets the new value of the current anchor. Detaches the old anchor, if it was non-null.
     */
    private void setNewAnchor(Anchor newAnchor) {
        synchronized (anchorLock) {
            if (anchor != null) {
                anchor.detach();
            }
            anchor = newAnchor;
            if (anchor != null) {
                depthImagePoints.clear();
                depthImagePoints.addAll(depthImagePointRepository.getDepthImagePoints(anchor.getPose()));
            }
        }
    }

    /**
     * Callback function invoked when the Host Button is pressed.
     *
     * @param view
     */
    private void onHostButtonPress(View view) {
        if (currentMode == HostResolveMode.HOSTING) {
            resetMode();
            return;
        }

        if (hostListener != null) {
            return;
        }
        resolveButton.setEnabled(false);
        hostButton.setText(R.string.cancel);
        snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_host));

        hostListener = new RoomCodeAndCloudAnchorIdListener();
        firebaseManager.getNewRoomCode(hostListener);
    }

    /**
     * Callback function invoked when the Resolve Button is pressed.
     *
     * @param view
     */
    private boolean onResolveButtonLongPress(View view) {
        if (currentMode == HostResolveMode.RESOLVING) {
            resetMode();
            return true;
        }
        ResolveDialogFragment dialogFragment = new ResolveDialogFragment();
        dialogFragment.setOkListener(this::onRoomCodeEntered);
        dialogFragment.show(getSupportFragmentManager(), "ResolveDialog");
        return true;
    }

    private void onResolveButtonPress(View view) {
        if (currentMode == HostResolveMode.RESOLVING) {
            resetMode();
            return;
        }
        firebaseManager.getLastRoomCode(new FirebaseManager.RoomCodeListener() {
            @Override
            public void onNewRoomCode(Long newRoomCode) {
                onRoomCodeEntered(newRoomCode);
            }

            @Override
            public void onError(DatabaseError error) {

            }
        });
    }

    /**
     * Resets the mode of the app to its initial state and removes the anchors.
     */
    private void resetMode() {
        hostButton.setText(R.string.host_button_text);
        hostButton.setEnabled(true);
        resolveButton.setText(R.string.resolve_button_text);
        resolveButton.setEnabled(true);
        roomCodeText.setText(R.string.initial_room_code);
        currentMode = HostResolveMode.NONE;
        firebaseManager.clearRoomListener();
        hostListener = null;
        setNewAnchor(null);
        snackbarHelper.hide(this);
        cloudManager.clearListeners();
    }

    /**
     * Callback function invoked when the user presses the OK button in the Resolve Dialog.
     */
    private void onRoomCodeEntered(Long roomCode) {
        currentMode = HostResolveMode.RESOLVING;
        hostButton.setEnabled(false);
        resolveButton.setText(R.string.cancel);
        roomCodeText.setText(String.valueOf(roomCode));
        snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));

        // Register a new listener for the given room.
        firebaseManager.registerNewListenerForRoom(
                roomCode,
                (cloudAnchorId) -> {
                    // When the cloud anchor ID is available from Firebase.
                    cloudManager.resolveCloudAnchor(
                            cloudAnchorId,
                            (anchor) -> {
                                // When the anchor has been resolved, or had a final error state.
                                CloudAnchorState cloudState = anchor.getCloudAnchorState();
                                if (cloudState.isError()) {
                                    Log.w(
                                            TAG,
                                            "The anchor in room "
                                                    + roomCode
                                                    + " could not be resolved. The error state was "
                                                    + cloudState);
                                    snackbarHelper.showMessageWithDismiss(
                                            CloudAnchorActivity.this,
                                            getString(R.string.snackbar_resolve_error, cloudState));
                                    return;
                                }
                                snackbarHelper.showMessageWithDismiss(
                                        CloudAnchorActivity.this, getString(R.string.snackbar_resolve_success));
                                setNewAnchor(anchor);
                            });
                });
    }

    private void onImageButtonClicked(View view) {
        if (leftDepthImagePointPose != null) {
            leftDepthImagePointPose = null;
            runOnUiThread(() -> Toast.makeText(this, "Stereo image capture canceled", Toast.LENGTH_SHORT).show());
        } else {
            saveImage("point_" + DepthImagePoint.getNumber() + "_l");
            leftDepthImagePointPose = frame.getCamera().getPose();
        }
    }

    private void captureDepthImagePointWithRightDistance(PointCloud pointCloud) {
        Pose cameraPose = frame.getCamera().getPose();
        if (leftDepthImagePointPose != null && Math.abs(PoseHelper.distance(leftDepthImagePointPose, cameraPose)) > 0.07f) {
            saveImage("point_" + DepthImagePoint.getNumber() + "_r");
            Pose anchorPose = anchor.getPose();
            Pose interpolatedPose = Pose.makeInterpolated(cameraPose, leftDepthImagePointPose, 0.5f);
            DepthImagePoint point = new DepthImagePoint(anchorPose.inverse().compose(interpolatedPose), cameraPose, "point");
            depthImagePointRepository.putDepthImagePoint(point);
            depthImagePoints.add(point);
            leftDepthImagePointPose = null;
            middleDepthImagePointPose = null;
            runOnUiThread(() -> Toast.makeText(this, "Stereo image capture finished", Toast.LENGTH_SHORT).show());
        } else if (middleDepthImagePointPose == null && Math.abs(PoseHelper.distance(leftDepthImagePointPose, cameraPose)) > 0.035f) {
            saveImage("point_" + DepthImagePoint.getNumber());
            middleDepthImagePointPose = cameraPose;
            PointCloudWriteHelper.save(pointCloud, cameraPose, "point_" + DepthImagePoint.getNumber());
        }
    }

    private void saveImage(String name) {
        try (Image image = frame.acquireCameraImage()) {
            ImageWriteHelper.saveImage(image, name);
        } catch (NotYetAvailableException e) {
            e.printStackTrace();
        }
    }

    private enum HostResolveMode {
        NONE,
        HOSTING,
        RESOLVING,
    }

    /**
     * Listens for both a new room code and an anchor ID, and shares the anchor ID in Firebase with
     * the room code when both are available.
     */
    private final class RoomCodeAndCloudAnchorIdListener
            implements CloudAnchorManager.CloudAnchorListener, FirebaseManager.RoomCodeListener {

        private Long roomCode;
        private String cloudAnchorId;

        @Override
        public void onNewRoomCode(Long newRoomCode) {
            Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
            roomCode = newRoomCode;
            roomCodeText.setText(String.valueOf(roomCode));
            snackbarHelper.showMessageWithDismiss(
                    CloudAnchorActivity.this, getString(R.string.snackbar_room_code_available));
            checkAndMaybeShare();
            synchronized (singleTapLock) {
                // Change currentMode to HOSTING after receiving the room code (not when the 'Host' button
                // is tapped), to prevent an anchor being placed before we know the room code and able to
                // share the anchor ID.
                currentMode = HostResolveMode.HOSTING;
            }
        }

        @Override
        public void onError(DatabaseError error) {
            Log.w(TAG, "A Firebase database error happened.", error.toException());
            snackbarHelper.showError(
                    CloudAnchorActivity.this, getString(R.string.snackbar_firebase_error));
        }

        @Override
        public void onCloudTaskComplete(Anchor anchor) {
            CloudAnchorState cloudState = anchor.getCloudAnchorState();
            if (cloudState.isError()) {
                Log.e(TAG, "Error hosting a cloud anchor, state " + cloudState);
                snackbarHelper.showMessageWithDismiss(
                        CloudAnchorActivity.this, getString(R.string.snackbar_host_error, cloudState));
                return;
            }
            Preconditions.checkState(
                    cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
            cloudAnchorId = anchor.getCloudAnchorId();
            setNewAnchor(anchor);
            checkAndMaybeShare();
        }

        private void checkAndMaybeShare() {
            if (roomCode == null || cloudAnchorId == null) {
                return;
            }
            firebaseManager.storeAnchorIdInRoom(roomCode, cloudAnchorId);
            snackbarHelper.showMessageWithDismiss(
                    CloudAnchorActivity.this, getString(R.string.snackbar_cloud_id_shared));
        }
    }
}
