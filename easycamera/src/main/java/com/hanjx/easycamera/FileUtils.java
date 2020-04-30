package com.hanjx.easycamera;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;

public class FileUtils {
    public static String getApplicationName(Context context) {
        try {
            PackageManager packageManager = context.getApplicationContext().getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            return packageManager.getApplicationLabel(applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "EasyCamera";
        }
    }

    public static File getOutputFile(Context context) {
        Context appContext = context.getApplicationContext();
        File[] mediaDirs = context.getExternalMediaDirs();
        if (mediaDirs != null && mediaDirs.length > 0 && mediaDirs[0] != null) {
            File appMediaDir = new File(mediaDirs[0], getApplicationName(context));
            appMediaDir.mkdirs();
            return appMediaDir;
        }
        return appContext.getFilesDir();
    }

    public static File createPhotoFile(File outputFile) {
        return new File(outputFile, String.format("%s%s", System.currentTimeMillis(), ".jpeg"));
    }
}
