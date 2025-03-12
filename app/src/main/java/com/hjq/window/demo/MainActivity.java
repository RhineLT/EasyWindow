package com.hjq.window.demo;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.io.File;
import android.util.Log;
import java.io.FileInputStream;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.FileOutputStream;
import android.graphics.Rect;
import android.view.PixelCopy;
import android.view.Window;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.view.View;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.hjq.bar.OnTitleBarListener;
import com.hjq.bar.TitleBar;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.hjq.toast.Toaster;
import com.hjq.window.EasyWindow;
import com.hjq.window.draggable.MovingDraggable;
import com.hjq.window.draggable.SpringBackDraggable;
import java.util.List;
import okhttp3.*;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjection;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.Image;
import java.nio.ByteBuffer;

public final class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request SYSTEM_ALERT_WINDOW permission
        XXPermissions.with(this)
            .permission(Permission.SYSTEM_ALERT_WINDOW)
            .request(new OnPermissionCallback() {
                @Override
                public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                    // Start screen recording
                    startScreenRecording();
                }

                @Override
                public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                    Toaster.show("Please grant overlay permission to continue");
                }
            });

        findViewById(R.id.btn_start_translation).setOnClickListener(this);

        TitleBar titleBar = findViewById(R.id.tb_main_bar);
        titleBar.setOnTitleBarListener(new OnTitleBarListener() {
            @Override
            public void onTitleClick(TitleBar titleBar) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(titleBar.getTitle().toString()));
                startActivity(intent);
            }
        });
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.btn_start_translation) {
            // Activate global top circular button
            activateGlobalButton();
        }
    }

    private void startScreenRecording() {
        // Implement screen recording logic here
    }

    private void activateGlobalButton() {
        SpringBackDraggable draggable = new SpringBackDraggable(SpringBackDraggable.ORIENTATION_HORIZONTAL);
        draggable.setAllowMoveToScreenNotch(false);

        EasyWindow.with(getApplication())
            .setContentView(R.layout.window_button)
            .setGravity(Gravity.END | Gravity.BOTTOM)
            .setYOffset(200)
            .setDraggable(draggable)
            .setOnClickListener(R.id.btn_global, new EasyWindow.OnClickListener<ImageView>() {
                @Override
                public void onClick(EasyWindow<?> easyWindow, ImageView view) {
                    // Capture and save the current frame
                    captureAndSaveFrame();
                }
            })
            .show();
    }

    private void captureAndSaveFrame() {
        // Capture the current frame
        // Save the frame as a time-formatted PNG image
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "IMG_" + timestamp + ".png";
        // Save the image to the app data directory

        // Send the image for translation
        sendImageForTranslation(fileName);
    }

    private void sendImageForTranslation(String fileName) {
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("multipart/form-data; boundary=---011000010111000001101001");
        RequestBody body = RequestBody.create(mediaType, "-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"config\"\r\n\r\n{                 \"detector\": {                     \"detector\": \"default\",                     \"detection_size\": 1536                 },                 \"render\": {                     \"direction\": \"auto\"                 },                 \"translator\": {                     \"translator\": \"gpt3.5\",                     \"target_lang\": \"CHS\"                 }             }\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"image\"\r\n\r\n[object Object]\r\n-----011000010111000001101001--\r\n\r\n");
        Request request = new Request.Builder()
            .url("http://47.94.2.169:777/translate/with-form/image")
            .post(body)
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("User-Agent", "PostmanRuntime-ApipostRuntime/1.1.0")
            .addHeader("Connection", "keep-alive")
            .addHeader("content-type", "multipart/form-data; boundary=---011000010111000001101001")
            .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                // Save the returned image with a '_trans' suffix
                String transFileName = fileName.replace(".png", "_trans.png");
                // Save the translated image to the app data directory
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}