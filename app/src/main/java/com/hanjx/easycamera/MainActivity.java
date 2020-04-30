package com.hanjx.easycamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.PreviewView;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.blankj.utilcode.util.ToastUtils;
import com.bumptech.glide.Glide;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private File outputFile;

    private PreviewView previewView;
    private ImageView resultView;
    private View takePicBtn;
    private View switchCameraImg;

    EasyCamera easyCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        outputFile = FileUtils.getOutputFile(this);

        previewView = findViewById(R.id.preview_view);
        takePicBtn = findViewById(R.id.take_photo_img);
        resultView = findViewById(R.id.result_img);
        switchCameraImg = findViewById(R.id.switch_camera);

        easyCamera = new EasyCamera(MainActivity.this, previewView);
        previewView.post(() -> configEasyCamera(easyCamera));

        takePicBtn.setOnClickListener(view -> easyCamera.takePicture(new EasyCamera.FileCallBack() {
            @Override
            public void onImageFileSaved(@NonNull Uri uri) {
                resultView.setVisibility(View.VISIBLE);
                Glide.with(resultView)
                        .load(uri)
                        .into(resultView);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                exception.printStackTrace();
            }
        }));

        resultView.setOnClickListener(v -> resultView.setVisibility(View.INVISIBLE));
        switchCameraImg.setOnClickListener(v -> easyCamera.switchCamera());
    }

    private void configEasyCamera(EasyCamera easyCamera) {
        easyCamera.setLensFacing(CameraSelector.LENS_FACING_BACK)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setRatio(AspectRatio.RATIO_4_3)
                .build(new EasyCamera.BuildCallBack() {
                    @Override
                    public void onCameraReady() { }

                    @Override
                    public void onPermissionDenied() {
                        ToastUtils.showShort("请同意权限请求");
                    }

                    @Override
                    public void onBuildFailed(Exception e) { }
                });
    }
}
