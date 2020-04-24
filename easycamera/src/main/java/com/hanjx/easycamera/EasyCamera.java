package com.hanjx.easycamera;

import android.Manifest;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.ImageView;

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
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.util.concurrent.ListenableFuture;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class EasyCamera {

    // Camera 实例
    private Camera camera;

    // 摄像头 前置/后置
    private int lensFacing;

    // 预览 view
    private PreviewView previewView;

    // UsesCase 实例
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private VideoCapture videoCapture;
    private List<UseCase> unknownUseCases;

    // 默认输出目录
    private File outputFile;

    private EasyCamera(Camera camera, PreviewView previewView, int lensFacing, UseCase... useCases) {
        this.camera = camera;
        this.previewView = previewView;
        this.lensFacing = lensFacing;
        this.outputFile = Utils.getOutputFile(previewView.getContext());
        for (UseCase useCase : useCases) {
            if (useCase instanceof Preview) {
                this.preview = (Preview) useCase;
            } else if (useCase instanceof ImageCapture) {
                this.imageCapture = (ImageCapture) useCase;
            } else if (useCase instanceof ImageAnalysis) {
                this.imageAnalysis = (ImageAnalysis) useCase;
            } else if (useCase instanceof VideoCapture) {
                this.videoCapture = (VideoCapture) useCase;
            } else {
                unknownUseCases.add(useCase);
            }
        }
    }

    public void takePicture(FileCallBack callBack) {
        takePicture(Utils.createPhotoFile(outputFile), callBack);
    }

    public void takePicture(DrawableCallBack callBack) {
        takePicture(false, Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL, callBack);
    }

    public void takePicture(boolean persistent, DrawableCallBack callBack) {
        takePicture(persistent, Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL, callBack);
    }

    public void takePicture(int width, int height, DrawableCallBack callBack) {
        takePicture(false, width, height, callBack);
    }

    public void takePicture(boolean persistent, int width, int height, DrawableCallBack callBack) {
        if (imageCapture != null) {
            File file;
            try {
                file = createImageFile(persistent);
            } catch (IOException e) {
                callBack.onError(e);
                return;
            }

            final File imageFile = file;
            final CustomTarget<Drawable> target = new CustomTarget<Drawable>(width, height) {
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

            imageCapture.takePicture(createOutputOptions(imageFile),
                    ContextCompat.getMainExecutor(previewView.getContext()),
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
        if (imageCapture != null) {
            imageCapture.takePicture(createOutputOptions(outputFile),
                    ContextCompat.getMainExecutor(previewView.getContext()),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                            callBack.onImageFileSaved(Uri.fromFile(outputFile));
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
        imageCapture.takePicture(createOutputOptions(imageFile),
                ContextCompat.getMainExecutor(previewView.getContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        Glide.with(imageView)
                                .load(Uri.fromFile(imageFile))
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

        private int ratio;
        private boolean autoRatio = true;
        private int rotation;

        private Preview preview;
        private ImageCapture imageCapture;

        private int lensFacing = CameraSelector.LENS_FACING_BACK;
        private int captureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;

        public Builder(LifecycleOwner lifecycleOwner, PreviewView previewView) {
            this.lifecycleOwner = lifecycleOwner;
            this.previewView = previewView;
        }

        public Builder setCamera(@CameraSelector.LensFacing int lensFacing) {
            this.lensFacing = lensFacing;
            return this;
        }

        public Builder setRatio(@AspectRatio.Ratio int ratio) {
            this.ratio = ratio;
            this.autoRatio = false;
            return this;
        }

        public Builder setCaptureMode(@ImageCapture.CaptureMode int captureMode) {
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

                    EasyCamera easyCamera = new EasyCamera(
                            camera, previewView, lensFacing, preview, imageCapture);
                    buildCallBack.onBuildSuccess(easyCamera);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    buildCallBack.onBuildFailed(e);
                }
            }, ContextCompat.getMainExecutor(previewView.getContext()));
        }

        @AspectRatio.Ratio
        private int getPreviewRatio() {
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
    }

    public interface BuildCallBack {
        void onBuildSuccess(EasyCamera easyCamera);
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
