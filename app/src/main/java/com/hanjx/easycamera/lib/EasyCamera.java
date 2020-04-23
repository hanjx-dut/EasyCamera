package com.hanjx.easycamera.lib;

import android.Manifest;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.util.concurrent.ListenableFuture;
import com.hanjx.easycamera.Utils;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;
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

    private File outputFile;

    public void takePicture(PictureFileCallBack callBack) {
        takePicture(Utils.createPhotoFile(outputFile), callBack);
    }

    public void takePicture(PictureDrawableCallBack callBack) {
        takePicture(false, Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL, callBack);
    }

    public void takePicture(boolean persistent, PictureDrawableCallBack callBack) {
        takePicture(persistent, Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL, callBack);
    }

    public void takePicture(int width, int height, PictureDrawableCallBack callBack) {
        takePicture(false, width, height, callBack);
    }

    public void takePicture(boolean persistent, int width, int height, PictureDrawableCallBack callBack) {
        if (imageCapture != null) {
            File file;
            try {
                file = createImageFile(persistent);
            } catch (IOException e) {
                callBack.onError(e);
                return;
            }
            final File imageFile = file;

            imageCapture.takePicture(createOutputOptions(imageFile), ContextCompat.getMainExecutor(previewView.getContext()),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Uri resultUri = outputFileResults.getSavedUri();
                            if (resultUri == null) resultUri = Uri.fromFile(imageFile);
                            Glide.with(previewView.getContext())
                                    .load(resultUri)
                                    .into(new CustomTarget<Drawable>(width, height) {
                                        @Override
                                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                            callBack.onDrawableReady(resource);
                                            if (!persistent) {
                                                imageFile.delete();
                                            }
                                        }

                                        @Override
                                        public void onLoadCleared(@Nullable Drawable placeholder) { }

                                        @Override
                                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                            super.onLoadFailed(errorDrawable);
                                            callBack.onError(new Exception("Drawable load failed"));
                                            if (!persistent) {
                                                imageFile.delete();
                                            }
                                        }
                                    });
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            if (!persistent) {
                                imageFile.delete();
                            }
                        }
                    });
        }
    }

    public void takePicture(File outputFile, @NonNull PictureFileCallBack callBack) {
        if (imageCapture != null) {
            imageCapture.takePicture(createOutputOptions(outputFile), ContextCompat.getMainExecutor(previewView.getContext()),
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

    public void takePictureAndLoad(ImageView imageView) {
        File file;
        try {
            file = createImageFile(false);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        final File imageFile = file;
        imageCapture.takePicture(createOutputOptions(imageFile), ContextCompat.getMainExecutor(previewView.getContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri resultUri = outputFileResults.getSavedUri();
                        if (resultUri == null) resultUri = Uri.fromFile(imageFile);
                        Glide.with(imageView)
                                .load(resultUri)
                                .into(imageView);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                });
    }

    private ImageCapture.OutputFileOptions createOutputOptions(File imageFile) {
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        metadata.setReversedHorizontal(lensFacing == CameraSelector.LENS_FACING_FRONT);
        return new ImageCapture.OutputFileOptions.Builder(imageFile).setMetadata(metadata).build();
    }

    private File createImageFile(boolean persistent) throws IOException{
        if (persistent) {
            return Utils.createPhotoFile(outputFile);
        } else {
            return File.createTempFile("EasyCamera", ".tmp");
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

        private void build(BuildCallBack buildCallBack) {
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
                    easyCamera.outputFile = Utils.getOutputFile(previewView.getContext());
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

    public interface BuildCallBack {
        void onBuildSuccess(EasyCamera easyCamera);
        void onPermissionDenied();
        void onBuildFailed(Exception e);
    }

    public interface PictureFileCallBack {
        void onImageSaved(@NonNull Uri uri);
        void onError(@NonNull ImageCaptureException exception);
    }

    public interface PictureDrawableCallBack {
        void onDrawableReady(@NonNull Drawable drawable);
        void onError(@NonNull Exception exception);
    }
}
