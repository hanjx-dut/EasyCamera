package com.hanjx.easycamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.PreviewView;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.blankj.utilcode.util.ToastUtils;
import com.bumptech.glide.Glide;
import com.hanjx.easycamera.lib.EasyCamera;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private File outputFile;

    private PreviewView previewView;
    private ImageView resultView;
    private View takePicBtn;

    EasyCamera easyCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        outputFile = Utils.getOutputFile(this);

        previewView = findViewById(R.id.preview_view);
        takePicBtn = findViewById(R.id.take_photo_img);
        resultView = findViewById(R.id.result_img);

        previewView.post(() -> new EasyCamera.Builder(MainActivity.this, previewView)
                .chooseCamera(CameraSelector.LENS_FACING_BACK)
                .setRatio(AspectRatio.RATIO_16_9)
                .setCaptureMode(EasyCamera.MODE_QUALITY_FIRST)
                .requestPermissionAndBuild(new EasyCamera.BuildCallBack() {
                    @Override
                    public void onBuildSuccess(EasyCamera easyCamera) {
                        MainActivity.this.easyCamera = easyCamera;
                    }

                    @Override
                    public void onPermissionDenied() {
                        ToastUtils.showShort("请同意权限请求");
                    }

                    @Override
                    public void onBuildFailed(Exception e) {
                        e.printStackTrace();
                    }
                }));

        takePicBtn.setOnClickListener(view -> {
            if (easyCamera != null) {
                easyCamera.takePicture(new EasyCamera.PictureFileCallBack() {
                    @Override
                    public void onImageSaved(@NonNull Uri uri) {
                        resultView.setVisibility(View.VISIBLE);
                        Glide.with(resultView)
                                .load(uri)
                                .into(resultView);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                });
            }
        });

        resultView.setOnClickListener(v -> resultView.setVisibility(View.INVISIBLE));
    }
}
