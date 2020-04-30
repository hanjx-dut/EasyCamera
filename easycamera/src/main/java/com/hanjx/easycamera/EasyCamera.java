package com.hanjx.easycamera;

import android.Manifest;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.util.concurrent.ListenableFuture;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EasyCamera {

    private boolean cameraReady = false;

    private LifecycleOwner lifecycleOwner;

    private Camera camera;

    private PreviewView previewView;

    private ExecutorService cameraExecutor;

    // UsesCase 实例
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private VideoCapture videoCapture;
    private List<UseCase> unknownUseCases;

    private File outputFile;  // 默认输出目录

    private int lensFacing;  // 摄像头 前置/后置

    private int ratio;  // 照片比例

    private int rotation;  // 旋转角度

    private int captureMode;  // 拍照模式：速度优先/质量优先

    private boolean configChanged = false;

    public EasyCamera(LifecycleOwner lifecycleOwner, PreviewView previewView) {
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        lifecycleOwner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                cameraExecutor.shutdown();
            }
        });
        initDefault();
    }

    private void initDefault() {
        this.outputFile = FileUtils.getOutputFile(previewView.getContext());

    }

    public EasyCamera setLensFacing(@CameraSelector.LensFacing int lensFacing) {
        if (this.lensFacing != lensFacing) {
            configChanged = true;
            this.lensFacing = lensFacing;
        }
        return this;
    }

    public EasyCamera setRatio(int ratio) {
        if (this.ratio != ratio) {
            configChanged = true;
            this.ratio = ratio;
        }
        return this;
    }

    public EasyCamera setRotation(int rotation) {
        if (this.rotation != rotation) {
            configChanged = true;
            this.rotation = rotation;
        }
        return this;
    }

    public EasyCamera setCaptureMode(int captureMode) {
        if (this.captureMode != captureMode) {
            configChanged = true;
            this.captureMode = captureMode;
        }
        return this;
    }

    public EasyCamera setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    public void switchCamera() {
        switchCamera(null);
    }

    public void switchCamera(@Nullable BuildCallBack buildCallBack) {
        switchCamera(lensFacing == CameraSelector.LENS_FACING_BACK ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK, buildCallBack);
    }

    public void switchCamera(@CameraSelector.LensFacing int lensFacing, @Nullable BuildCallBack buildCallBack) {
        setLensFacing(lensFacing);
        rebuild(buildCallBack);
    }

    public void build(@Nullable BuildCallBack buildCallBack) {
        checkPermissionAndBuild(buildCallBack);
    }

    public void rebuild(@Nullable BuildCallBack buildCallBack) {
        if (!cameraReady || configChanged) {
            cameraReady = false;
            build(buildCallBack);
        }
    }

    private void checkPermissionAndBuild(@Nullable BuildCallBack buildCallBack) {
        new RxPermissions((AppCompatActivity) previewView.getContext())
                .request(Manifest.permission.CAMERA)
                .subscribe(granted -> {
                    if (granted) {
                        bindCameraUseCases(buildCallBack);
                    } else {
                        if (buildCallBack != null) {
                            buildCallBack.onPermissionDenied();
                        }
                    }
                });
    }

    private void bindCameraUseCases(@Nullable BuildCallBack buildCallBack) {
        if (ratio == 0) {
            ratio = matchPreviewRatio();
        }
        CameraSelector cameraSelector =
                new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(previewView.getContext());
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = providerFuture.get();
                cameraProvider.unbindAll();

                rotation = previewView.getDisplay().getRotation();

                initUseCase();

                Camera camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture);

                preview.setSurfaceProvider(
                        previewView.createSurfaceProvider(camera.getCameraInfo()));

                cameraReady = true;
                configChanged = false;
                if (buildCallBack != null) {
                    buildCallBack.onCameraReady();
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                if (buildCallBack != null) {
                    buildCallBack.onBuildFailed(e);
                }
            }
        }, ContextCompat.getMainExecutor(previewView.getContext()));
    }

    public void takePicture(FileCallBack callBack) {
        takePicture(FileUtils.createPhotoFile(outputFile), callBack);
    }

    public void takePicture(DrawableCallBack callBack) {
        takePicture(false, callBack);
    }

    public void takePicture(boolean persistent, DrawableCallBack callBack) {
        if (cameraReady && imageCapture != null) {
            File file;
            try {
                file = createImageFile(persistent);
            } catch (IOException e) {
                callBack.onError(e);
                return;
            }

            final File imageFile = file;
            final CustomTarget<Drawable> target = new CustomTarget<Drawable>() {
                @Override
                public void onResourceReady(@NonNull Drawable resource,
                                            @Nullable Transition<? super Drawable> transition) {
                    callBack.onDrawableReady(resource);
                    onFinish();
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) { }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    super.onLoadFailed(errorDrawable);
                    callBack.onError(new Exception("Drawable load failed"));
                    onFinish();
                }

                private void onFinish() {
                    if (!persistent) {
                        imageFile.delete();
                    }
                }
            };

            imageCapture.takePicture(createOutputOptions(imageFile), cameraExecutor,
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                            Glide.with(previewView.getContext())
                                    .load(Uri.fromFile(imageFile))
                                    .into(target);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            callBack.onError(exception);
                            if (!persistent) {
                                imageFile.delete();
                            }
                        }
                    });
        }
    }

    public void takePicture(File outputFile, @NonNull FileCallBack callBack) {
        if (cameraReady && imageCapture != null) {
            imageCapture.takePicture(createOutputOptions(outputFile), cameraExecutor,
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                            previewView.post(() -> callBack.onImageFileSaved(Uri.fromFile(outputFile)));
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            previewView.post(() -> callBack.onError(exception));
                        }
                    });
        }
    }

    @AspectRatio.Ratio
    private int matchPreviewRatio() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        previewView.getDisplay().getRealMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        double previewRatio = ((double) Math.max(width, height)) / Math.min(width, height);
        if (Math.abs(previewRatio - AspectRatio.RATIO_4_3)
                <= Math.abs(previewRatio - AspectRatio.RATIO_16_9)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    private void initUseCase() {
        preview = new Preview.Builder()
                .setTargetAspectRatio(ratio)
                .setTargetRotation(rotation)
                .build();

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(captureMode)
                .setTargetAspectRatio(ratio)
                .setTargetRotation(rotation)
                .build();

        setOrientationListener();
    }

    private void setOrientationListener() {
        new OrientationEventListener(previewView.getContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }
                imageCapture.setTargetRotation(rotation);
            }
        }.enable();
    }

    private ImageCapture.OutputFileOptions createOutputOptions(File imageFile) {
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        metadata.setReversedHorizontal(lensFacing == CameraSelector.LENS_FACING_FRONT);
        return new ImageCapture.OutputFileOptions.Builder(imageFile).setMetadata(metadata).build();
    }

    private File createImageFile(boolean persistent) throws IOException {
        if (persistent) {
            return FileUtils.createPhotoFile(outputFile);
        } else {
            return File.createTempFile("EasyCamera", ".tmp");
        }
    }

    public boolean isCameraReady() {
        return cameraReady;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public int getLensFacing() {
        return lensFacing;
    }

    public int getRatio() {
        return ratio;
    }

    public int getRotation() {
        return rotation;
    }

    public int getCaptureMode() {
        return captureMode;
    }

    public boolean isConfigChanged() {
        return configChanged;
    }

    public interface BuildCallBack {
        void onCameraReady();
        void onPermissionDenied();
        void onBuildFailed(Exception e);
    }

    public interface FileCallBack {
        void onImageFileSaved(@NonNull Uri uri);
        void onError(@NonNull ImageCaptureException exception);
    }

    public interface DrawableCallBack {
        void onDrawableReady(@NonNull Drawable drawable);
        void onError(@NonNull Exception exception);
    }
}
