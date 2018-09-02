package com.google.ar.core.examples.java.cloudanchor;

import com.google.ar.core.Pose;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.converter.PropertyConverter;

@Entity
public class DepthImagePoint {

    private static int number = 0;

    @Id
    private long id;

    private transient Pose poseRelativeToOrigin;

    @Convert(converter = PoseConverter.class, dbType = String.class)
    private Pose poseRelativeToAnchor;
    private String name;

    public DepthImagePoint() {
        // Required empty constructor for ObjectBox
    }

    public DepthImagePoint(Pose poseRelativeToAnchor, Pose poseRelativeToOrigin, String name) {
        this.poseRelativeToAnchor = poseRelativeToAnchor;
        this.poseRelativeToOrigin = poseRelativeToOrigin;
        this.name = name + "_" + number;
        number++;
    }

    public static int getNumber() {
        return number;
    }

    public static void resetNumber() {
        number = 0;
    }

    public void updatePoseRelativeToOrigin(Pose anchorPose) {
        this.poseRelativeToOrigin = anchorPose.compose(poseRelativeToAnchor);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Pose getPoseRelativeToOrigin() {
        return poseRelativeToOrigin;
    }

    public void setPoseRelativeToOrigin(Pose poseRelativeToOrigin) {
        this.poseRelativeToOrigin = poseRelativeToOrigin;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Pose getPoseRelativeToAnchor() {
        return poseRelativeToAnchor;
    }

    public void setPoseRelativeToAnchor(Pose poseRelativeToAnchor) {
        this.poseRelativeToAnchor = poseRelativeToAnchor;
    }

    public static class PoseConverter implements PropertyConverter<Pose, String> {

        private static final String SEPARATOR = " ";

        @Override
        public Pose convertToEntityProperty(String s) {
            float[] translation = new float[3];
            float[] rotation = new float[4];
            String[] split = s.split(SEPARATOR);
            for (int i = 0; i < translation.length; i++) {
                translation[i] = Float.parseFloat(split[i]);
            }
            for (int i = 0; i < rotation.length; i++) {
                rotation[i] = Float.parseFloat(split[i + translation.length]);
            }
            return new Pose(translation, rotation);
        }

        @Override
        public String convertToDatabaseValue(Pose p) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder
                    .append(p.tx()).append(SEPARATOR)
                    .append(p.ty()).append(SEPARATOR)
                    .append(p.tz()).append(SEPARATOR)
                    .append(p.qx()).append(SEPARATOR)
                    .append(p.qy()).append(SEPARATOR)
                    .append(p.qz()).append(SEPARATOR)
                    .append(p.qw());
            return stringBuilder.toString();
        }
    }
}
