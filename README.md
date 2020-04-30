# EasyCamera
CameraX 二次封装

```Groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.hanjx-dut:EasyCamera:0.2'
}
```

## 使用详见Demo
+ 布局中包含一个 PreviewView，或者 new 出来也可
```XML
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <androidx.camera.view.PreviewView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```
+ 初始化相机要在主线程
```Java
    // 初始化相机
    EasyCamera easyCamera = new EasyCamera(MainActivity.this, previewView);
    previewView.post(() -> configEasyCamera(easyCamera));  // 使用 post 保证处于主线程

private void configEasyCamera(EasyCamera easyCamera) {
    easyCamera.setLensFacing(CameraSelector.LENS_FACING_BACK)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setRatio(AspectRatio.RATIO_4_3)
            .build(new EasyCamera.BuildCallBack() {
                @Override
                public void onCameraReady() {
                    // 此回调中相机初始化完毕
                }

                @Override
                public void onPermissionDenied() {
                    ToastUtils.showShort("请同意权限请求");
                }

                @Override
                public void onBuildFailed(Exception e) { }
            });
}

// 触发拍照功能
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
```