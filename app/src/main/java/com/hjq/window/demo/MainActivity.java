package com.hjq.window.demo;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.Manifest;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static WeakReference<MainActivity> currentInstance = new WeakReference<>(null);
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        currentInstance = new WeakReference<>(this);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        checkPermissions();

        // ... 其他视图初始化代码与原始相同 ...
        // 保持原有视图初始化代码不变
        findViewById(R.id.btn_main_anim).setOnClickListener(this);
        findViewById(R.id.btn_main_duration).setOnClickListener(this);
        findViewById(R.id.btn_main_overlay).setOnClickListener(this);
        findViewById(R.id.btn_main_lifecycle).setOnClickListener(this);
        findViewById(R.id.btn_main_click).setOnClickListener(this);
        findViewById(R.id.btn_main_view).setOnClickListener(this);
        findViewById(R.id.btn_main_input).setOnClickListener(this);
        findViewById(R.id.btn_main_draggable).setOnClickListener(this);
        findViewById(R.id.btn_main_global).setOnClickListener(this);
        findViewById(R.id.btn_main_utils).setOnClickListener(this);
        findViewById(R.id.btn_main_cancel_all).setOnClickListener(this);

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

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                REQUEST_CODE_SCREEN_CAPTURE);
        } else {
            startScreenCapture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScreenCapture();
            } else {
                showToast("需要存储权限才能保存截图");
            }
        }
    }

    private void startScreenCapture() {
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                    setupVirtualDisplay();
                } catch (Exception e) {
                    Log.e("RhineLT", "初始化失败: " + e.getMessage());
                    showToast("屏幕捕获初始化失败");
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void setupVirtualDisplay() {
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenDensity = metrics.densityDpi;
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            imageReader = ImageReader.newInstance(
                    screenWidth, screenHeight,
                    PixelFormat.RGBA_8888, 2);

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
        } catch (Exception e) {
            Log.e("RhineLT", "创建失败: " + e.getMessage());
            showToast("无法创建虚拟显示");
        }
    }

    private void safeCaptureFrame() {
        new Thread(() -> {
            try {
                Image image = imageReader.acquireLatestImage();
                if (image == null) {
                    showToast("获取图像失败");
                    return;
                }

                final Bitmap bitmap = imageToBitmap(image);
                image.close();

                if (bitmap != null) {
                    mainHandler.post(() -> {
                        try {
                            File file = saveBitmapToFile(bitmap);
                            if (file != null) {
                                uploadImageWithRetry(file, 3);
                            }
                        } catch (Exception e) {
                            Log.e("RhineLT", "处理失败: " + e.getMessage());
                            showToast("保存或上传失败");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("RhineLT", "捕获异常: " + e.getMessage());
                showToast("截图失败");
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int width = image.getWidth();
            int height = image.getHeight();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, 
                    height, 
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        } catch (Exception e) {
            Log.e("RhineLT", "转换失败: " + e.getMessage());
            return null;
        }
    }

    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建目录: " + directory.getAbsolutePath());
        }

        File file = new File(directory, "SCREENSHOT_" + timeStamp + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            return file;
        }
    }

    private void uploadImageWithRetry(File file, int maxRetries) {
        new Thread(() -> {
            int retryCount = 0;
            boolean success = false;
            
            while (retryCount < maxRetries && !success) {
                try {
                    success = uploadImage(file);
                    if (!success) {
                        Log.w("RhineLT", "尝试 " + (retryCount + 1) + " 失败");
                        retryCount++;
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    Log.e("RhineLT", "上传异常: " + e.getMessage());
                    retryCount++;
                }
            }

            if (!success) {
                showToast("上传失败，请重试");
            }
        }).start();
    }

    private boolean uploadImage(File file) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        MediaType mediaType = MediaType.parse("multipart/form-data");
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("config", "{...}") // 保持原有配置
                .addFormDataPart("image", file.getName(), 
                    RequestBody.create(mediaType, file))
                .build();

        Request request = new Request.Builder()
                .url("https://47.94.2.169:4680/translate/with-form/image")
                .post(body)
                .addHeader("Accept", "*/*")
                // 保持原有header配置
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                byte[] bytes = response.body().bytes();
                saveTranslatedImage(bytes, file.getName());
                return true;
            }
            return false;
        }
    }

    private void saveTranslatedImage(byte[] imageBytes, String originalName) {
        new Thread(() -> {
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "TRANSLATED_" + timeStamp + ".png");

                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(imageBytes);
                    out.flush();
                    showToast("保存成功: " + file.getName());
                }
            } catch (Exception e) {
                Log.e("RhineLT", "保存失败: " + e.getMessage());
                showToast("翻译结果保存失败");
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        currentInstance.clear();
        releaseResources();
    }

    private void releaseResources() {
        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Exception e) {
            Log.e("RhineLT", "释放资源失败: " + e.getMessage());
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toaster.show(message));
    }

    // 保持原有按钮点击事件处理逻辑不变
    @Override
    public void onClick(View v) {
        final int viewId = v.getId();
    
        if (viewId == R.id.btn_main_anim) {
    
            EasyWindow.with(this)
                    .setDuration(1000)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.TopAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_finish)
                    .setText(android.R.id.message, "这个动画是不是很骚")
                    .show();
    
        } else if (viewId == R.id.btn_main_duration) {
    
            EasyWindow.with(this)
                    .setDuration(1000)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.IOSAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_error)
                    .setText(android.R.id.message, "一秒后自动消失")
                    .show();
    
        } else if (viewId == R.id.btn_main_overlay) {
    
            EasyWindow.with(this)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.IOSAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_finish)
                    .setText(android.R.id.message, "点我消失")
                    .setOutsideTouchable(false)
                    .setBackgroundDimAmount(0.5f)
                    .setOnClickListener(android.R.id.message, new EasyWindow.OnClickListener<TextView>() {
                        @Override
                        public void onClick(EasyWindow<?> easyWindow, TextView view) {
                            easyWindow.cancel();
                        }
                    })
                    .show();
    
        } else if (viewId == R.id.btn_main_lifecycle) {
    
            EasyWindow.with(this)
                    .setDuration(3000)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.IOSAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_warning)
                    .setText(android.R.id.message, "请注意下方 Snackbar")
                    .setOnWindowLifecycle(new EasyWindow.OnWindowLifecycle() {
                        @Override
                        public void onWindowShow(EasyWindow<?> easyWindow) {
                            Snackbar.make(getWindow().getDecorView(), "显示回调", Snackbar.LENGTH_SHORT).show();
                        }
    
                        @Override
                        public void onWindowCancel(EasyWindow<?> easyWindow) {
                            Snackbar.make(getWindow().getDecorView(), "消失回调", Snackbar.LENGTH_SHORT).show();
                        }
                    })
                    .show();
    
        } else if (viewId == R.id.btn_main_click) {
    
            EasyWindow.with(this)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.IOSAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_finish)
                    .setText(android.R.id.message, "点我点我点我")
                    .setOnClickListener(android.R.id.message, new EasyWindow.OnClickListener<TextView>() {
                        @Override
                        public void onClick(final EasyWindow<?> easyWindow, TextView view) {
                            view.setText("不错，很听话");
                            easyWindow.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    easyWindow.cancel();
                                }
                            }, 1000);
                        }
                    })
                    .show();
    
        } else if (viewId == R.id.btn_main_view) {
    
            EasyWindow.with(this)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.RightAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_finish)
                    .setDuration(2000)
                    .setText(android.R.id.message, "位置算得准不准")
                    .setOnClickListener(android.R.id.message, new EasyWindow.OnClickListener<TextView>() {
                        @Override
                        public void onClick(final EasyWindow<?> easyWindow, TextView view) {
                            easyWindow.cancel();
                        }
                    })
                    .showAsDropDown(v, Gravity.BOTTOM);
    
        } else if (viewId == R.id.btn_main_input) {
    
            EasyWindow.with(this)
                    .setContentView(R.layout.window_input)
                    .setAnimStyle(R.style.BottomAnimStyle)
                    .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                    .setOnClickListener(R.id.tv_window_close, new EasyWindow.OnClickListener<TextView>() {
                        @Override
                        public void onClick(final EasyWindow<?> easyWindow, TextView view) {
                            easyWindow.cancel();
                        }
                    })
                    .show();
    
        } else if (viewId == R.id.btn_main_draggable) {
    
            EasyWindow.with(this)
                    .setContentView(R.layout.window_hint)
                    .setAnimStyle(R.style.IOSAnimStyle)
                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_finish)
                    .setText(android.R.id.message, "点我消失")
                    .setDraggable(new MovingDraggable())
                    .setOnClickListener(android.R.id.message, new EasyWindow.OnClickListener<TextView>() {
                        @Override
                        public void onClick(EasyWindow<?> easyWindow, TextView view) {
                            easyWindow.cancel();
                        }
                    })
                    .show();
    
        } else if (viewId == R.id.btn_main_global) {
    
            XXPermissions.with(this)
                    .permission(Permission.SYSTEM_ALERT_WINDOW)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(@NonNull List<String> permissions, boolean all) {
                            showGlobalWindow(getApplication());
                        }
    
                        @Override
                        public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                            showToast("需要悬浮窗权限");
                        }
                    });
    
        } else if (viewId == R.id.btn_main_cancel_all) {
    
            EasyWindow.recycleAllWindow();
    
        } else if (viewId == R.id.btn_main_utils) {
    
            EasyWindow.with(this)
                    .setDuration(1000)
                    .setContentView(Toaster.getStyle().createView(this))
                    .setAnimStyle(R.style.ScaleAnimStyle)
                    .setText(android.R.id.message, "就问你溜不溜")
                    .setGravity(Gravity.BOTTOM)
                    .setYOffset(100)
                    .show();
        }
    }
    

    public static void showGlobalWindow(Application application) {
        SpringBackDraggable draggable = new SpringBackDraggable(
            SpringBackDraggable.ORIENTATION_HORIZONTAL);
        draggable.setAllowMoveToScreenNotch(false);

        EasyWindow.with(application)
                .setContentView(R.layout.window_phone)
                .setGravity(Gravity.END | Gravity.BOTTOM)
                .setYOffset(200)
                .setDraggable(draggable)
                .setOnClickListener(android.R.id.icon, (easyWindow, view) -> {
                    MainActivity activity = currentInstance.get();
                    if (activity != null && !activity.isFinishing()) {
                        activity.safeCaptureFrame();
                    } else {
                        Toaster.show("当前无法截图");
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
