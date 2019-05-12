package com.gromyk.mlkit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

/**
 * Graphic instance for rendering face contours graphic overlay view.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class FaceContourGraphic extends GraphicOverlay.Graphic {

    private static final float FACE_POSITION_RADIUS = 7.0f;
    private static final float ID_TEXT_SIZE = 50.0f;
    private static final float ID_Y_OFFSET = 80.0f;
    private static final float ID_X_OFFSET = -70.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int[] COLOR_CHOICES = {
            Color.BLUE, Color.GREEN, Color.CYAN,
            Color.MAGENTA, Color.RED, Color.WHITE, Color.YELLOW
    };
    private static int currentColorIndex = 0;

    private final Paint facePositionPaint;
    private final Paint idPaint;
    private final Paint boxPaint;

    private boolean drawBoundingBox;
    private boolean drawFaceContour;
    private boolean drawSmilingProbability;
    private boolean drawRightEyeOpenProbability;
    private boolean drawLeftEyeOpenProbability;
    private boolean drawRightEyeLandmark;
    private boolean drawLeftEyeLandmark;
    private boolean drawLeftCheekLandmark;
    private boolean drawRightCheekLandmark;

    private boolean drawId;

    private volatile FirebaseVisionFace firebaseVisionFace;

    public FaceContourGraphic(GraphicOverlay overlay) {
        super(overlay);

        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[currentColorIndex];
        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.length;
        final int textColor = COLOR_CHOICES[currentColorIndex];

        facePositionPaint = new Paint();
        facePositionPaint.setColor(selectedColor);

        idPaint = new Paint();
        idPaint.setColor(textColor);
        idPaint.setTextSize(ID_TEXT_SIZE);

        boxPaint = new Paint();
        boxPaint.setColor(selectedColor);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    /**
     * Updates the face instance from the detection of the most recent frame. Invalidates the relevant
     * portions of the overlay to trigger a redraw.
     */
    public void updateFace(FirebaseVisionFace face) {
        firebaseVisionFace = face;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        FirebaseVisionFace face = firebaseVisionFace;
        if (face == null) {
            return;
        }
        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getBoundingBox().centerX());
        float y = translateY(face.getBoundingBox().centerY());
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint);
        if (isDrawId())
            canvas.drawText("id: " + face.getTrackingId(), x + ID_X_OFFSET, y + ID_Y_OFFSET, idPaint);
        drawBoundingBox(canvas, face, new PointF(x, y));
        drawFaceContour(canvas, face);
        if (face.getSmilingProbability() >= 0 && isDrawSmilingProbability()) {
            canvas.drawText(
                    "smiling: " + String.format("%.2f", face.getSmilingProbability()),
                    x + ID_X_OFFSET * 3,
                    y + ID_Y_OFFSET * 2,
                    idPaint);
        }
        if (face.getRightEyeOpenProbability() >= 0 && isDrawRightEyeOpenProbability()) {
            canvas.drawText(
                    "right eye: " + String.format("%.2f", face.getRightEyeOpenProbability()),
                    x - ID_X_OFFSET,
                    y,
                    idPaint);
        }
        if (face.getLeftEyeOpenProbability() >= 0 && isDrawLeftEyeOpenProbability()) {
            canvas.drawText(
                    "left eye: " + String.format("%.2f", face.getLeftEyeOpenProbability()),
                    x + ID_X_OFFSET * 6,
                    y,
                    idPaint);
        }
        if (isDrawRightEyeLandmark())
            drawLandmark(canvas, face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE));
        if (isDrawRightEyeLandmark())
            drawLandmark(canvas, face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE));
        if (isDrawLeftEyeLandmark())
            drawLandmark(canvas, face.getLandmark(FirebaseVisionFaceLandmark.LEFT_CHEEK));
        if (isDrawRightCheekLandmark())
            drawLandmark(canvas, face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK));

    }

    private void drawBoundingBox(Canvas canvas, FirebaseVisionFace face, PointF point) {
        if (isDrawBoundingBox()) {
            float xOffset = scaleX(face.getBoundingBox().width() / 2.0f);
            float yOffset = scaleY(face.getBoundingBox().height() / 2.0f);
            float left = point.x - xOffset;
            float top = point.y - yOffset;
            float right = point.x + xOffset;
            float bottom = point.y + yOffset;
            canvas.drawRect(left, top, right, bottom, boxPaint);
        }
    }

    private void drawFaceContour(Canvas canvas, FirebaseVisionFace face) {
        if (isDrawFaceContour()) {
            FirebaseVisionFaceContour contour = face.getContour(FirebaseVisionFaceContour.ALL_POINTS);
            for (com.google.firebase.ml.vision.common.FirebaseVisionPoint point : contour.getPoints()) {
                float px = translateX(point.getX());
                float py = translateY(point.getY());
                canvas.drawCircle(px, py, FACE_POSITION_RADIUS, facePositionPaint);
            }
        }
    }

    private void drawLandmark(Canvas canvas, FirebaseVisionFaceLandmark faceLandmark) {
        if (faceLandmark != null && faceLandmark.getPosition() != null) {
            canvas.drawCircle(
                    translateX(faceLandmark.getPosition().getX()),
                    translateY(faceLandmark.getPosition().getY()),
                    FACE_POSITION_RADIUS,
                    facePositionPaint);
        }
    }


    // getters and setters for drawings flags


    public boolean isDrawBoundingBox() {
        return drawBoundingBox;
    }

    public void setDrawBoundingBox(boolean drawBoundingBox) {
        this.drawBoundingBox = drawBoundingBox;
    }

    public boolean isDrawFaceContour() {
        return drawFaceContour;
    }

    public void setDrawFaceContour(boolean drawFaceContour) {
        this.drawFaceContour = drawFaceContour;
    }

    public boolean isDrawSmilingProbability() {
        return drawSmilingProbability;
    }

    public void setDrawSmilingProbability(boolean drawSmilingProbability) {
        this.drawSmilingProbability = drawSmilingProbability;
    }

    public boolean isDrawRightEyeOpenProbability() {
        return drawRightEyeOpenProbability;
    }

    public void setDrawRightEyeOpenProbability(boolean drawRightEyeOpenProbability) {
        this.drawRightEyeOpenProbability = drawRightEyeOpenProbability;
    }

    public boolean isDrawLeftEyeOpenProbability() {
        return drawLeftEyeOpenProbability;
    }

    public void setDrawLeftEyeOpenProbability(boolean drawLeftEyeOpenProbability) {
        this.drawLeftEyeOpenProbability = drawLeftEyeOpenProbability;
    }

    public boolean isDrawRightEyeLandmark() {
        return drawRightEyeLandmark;
    }

    public void setDrawRightEyeLandmark(boolean drawRightEyeLandmark) {
        this.drawRightEyeLandmark = drawRightEyeLandmark;
    }

    public boolean isDrawLeftEyeLandmark() {
        return drawLeftEyeLandmark;
    }

    public void setDrawLeftEyeLandmark(boolean drawLeftEyeLandmark) {
        this.drawLeftEyeLandmark = drawLeftEyeLandmark;
    }

    public boolean isDrawLeftCheekLandmark() {
        return drawLeftCheekLandmark;
    }

    public void setDrawLeftCheekLandmark(boolean drawLeftCheekLandmark) {
        this.drawLeftCheekLandmark = drawLeftCheekLandmark;
    }

    public boolean isDrawRightCheekLandmark() {
        return drawRightCheekLandmark;
    }

    public void setDrawRightCheekLandmark(boolean drawRightCheekLandmark) {
        this.drawRightCheekLandmark = drawRightCheekLandmark;
    }

    public boolean isDrawId() {
        return drawId;
    }

    public void setDrawId(boolean drawId) {
        this.drawId = drawId;
    }
}
