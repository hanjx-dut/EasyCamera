package com.hanjx.easycamera.lib;

import android.Manifest;
import android.net.Uri;
import android.util.DisplayMetrics;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.concurrent.ExecutionException;


public class EasyCamera {
    public final static int MODE_SPEED_FIRST = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;
    public final static int MODE_QUALITY_FIRST = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY;
    @IntDef({MODE_SPEED_FIRST, MODE_QUALITY_FIRST})
    private @interface CaptureMode{}

    private Camera camera;

    private PreviewView previewView;
    private Preview preview;
    private ImageCapture imageCapture;

    private int lensFacing;

    public void takePicture(File outputFile, @NonNull PictureTakeCallBack callBack) {
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        metadata.setReversedHorizontal(lensFacing == CameraSelector.LENS_FACING_FRONT);
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(outputFile).setMetadata(metadata).build();
        if (imageCapture != null) {
            imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(previewView.getContext()),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Uri resultUri = outputFileResults.getSavedUri();
                            callBack.onImageSaved(resultUri == null ? Uri.fromFile(outputFile) : resultUri);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            callBack.onError(exception);
                        }
                    });
        }
    }

    public static class Builder {
        private LifecycleOwner lifecycleOwner;
        private PreviewView previewView;

        private int ratio = AspectRatio.RATIO_16_9;
        private boolean autoRatio = true;

        private int cameraOrientation = CameraSelector.LENS_FACING_BACK;
        private int captureMode = MODE_SPEED_FIRST;

        public Builder(LifecycleOwner lifecycleOwner, PreviewView previewView) {
            this.lifecycleOwner = lifecycleOwner;
            this.previewView = previewView;
        }

        public Builder chooseCamera(@CameraSelector.LensFacing int orientation) {
            this.cameraOrientation = orientation;
            return this;
        }

        public Builder setRatio(@AspectRatio.Ratio int ratio) {
            this.ratio = ratio;
            this.autoRatio = false;
            return this;
        }

        public Builder setCaptureMode(@CaptureMode int captureMode) {
            this.captureMode = captureMode;
            return this;
        }

        public void requestPermissionAndBuild(@NonNull BuildCallBack buildCallBack) {
            new RxPermissions((AppCompatActivity) previewView.getContext())
                    .request(Manifest.permission.CAMERA)
                    .subscribe(granted -> {
                        if (granted) {
                            build(buildCallBack);
                        } else {
                            buildCallBack.onPermissionDenied();
                        }
                    });
        }

        public void build(BuildCallBack buildCallBack) {
            if (autoRatio) {
                setRatio(getPreviewRatio());
            }

            CameraSelector cameraSelector =
                    new CameraSelector.Builder().requireLensFacing(cameraOrientation).build();
            ListenableFuture<ProcessCameraProvider> providerFuture =
                    ProcessCameraProvider.getInstance(previewView.getContext());
            providerFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = providerFuture.get();
                    cameraProvider.unbindAll();

                    int rotation = getPreviewViewRotation();

                    Preview preview = new Preview.Builder()
                            .setTargetAspectRatio(ratio)
                            .setTargetRotation(rotation)
                            .build();

                    ImageCapture capture = new ImageCapture.Builder()
                            .setCaptureMode(captureMode)
                            .setTargetAspectRatio(ratio)
                            .setTargetRotation(rotation)
                            .build();

                    Camera camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture);
                    preview.setSurfaceProvider(previewView.createSurfaceProvider(camera.getCameraInfo()));

                    EasyCamera easyCamera = new EasyCamera();
                    easyCamera.imageCapture = capture;
                    easyCamera.lensFacing = cameraOrientation;
                    easyCamera.previewView = previewView;
                    easyCamera.camera = camera;
                    easyCamera.preview = preview;
                    buildCallBack.onBuildSuccess(easyCamera);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    buildCallBack.onBuildFailed(e);
                }
            }, ContextCompat.getMainExecutor(previewView.getContext()));
        }

        private int getPreviewViewRotation() {
            return previewView.getDisplay().getRotation();
        }

        @AspectRatio.Ratio
        private int getPreviewRatio() {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            previewView.getDisplay().getRealMetrics(displayMetrics);
            int width = displayMetrics.widthPixels;
            int height = displayMetrics.heightPixels;

            double previewRatio = ((double) Math.max(width, height)) / Math.min(width, height);
            if (Math.abs(previewRatio - AspectRatio.RATIO_4_3) <= Math.abs(previewRatio - AspectRatio.RATIO_16_9)) {
                return AspectRatio.RATIO_4_3;
            }
            return AspectRatio.RATIO_16_9;
        }
    }

    public interface PictureTakeCallBack {
        void onImageSaved(@NonNull Uri uri);
        void onError(@NonNull ImageCaptureException exception);
    }

    public interface BuildCallBack {
        void onBuildSuccess(EasyCamera easyCamera);
        void onPermissionDenied();
        void onBuildFailed(Exception e);
    }
}
