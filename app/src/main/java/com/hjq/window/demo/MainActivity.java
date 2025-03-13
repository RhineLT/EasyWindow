package com.hjq.window.demo;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.EditText;
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
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import android.Manifest;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.zip.GZIPInputStream;
import androidx.core.content.FileProvider;


public final class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static WeakReference<MainActivity> currentInstance = new WeakReference<>(null);
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText urlEditText;
    private EditText detectorEditText;
    private EditText detectionSizeEditText;
    private EditText translatorEditText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        currentInstance = new WeakReference<>(this);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        checkPermissions();
        findViewById(R.id.btn_main_global).setOnClickListener(this);
        TitleBar titleBar = findViewById(R.id.tb_main_bar);
        titleBar.setOnTitleBarListener(new OnTitleBarListener() {
            @Override
            public void onTitleClick(TitleBar titleBar) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(titleBar.getTitle().toString()));
                startActivity(intent);
            }
        });
        urlEditText = findViewById(R.id.et_url);
        detectorEditText = findViewById(R.id.et_detector);
        detectionSizeEditText = findViewById(R.id.et_detection_size);
        translatorEditText = findViewById(R.id.et_translator);
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
                showToast("Permissions are required to save screenshots.");
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
                    Log.e("RhineLT", "Initialization failed: " + e.getMessage(), e);
                    showToast("Screen capture initialization failed");
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
            Log.d("RhineLT", "VirtualDisplay creation successful");
        } catch (Exception e) {
            Log.e("RhineLT", "Failed to create VirtualDisplay: " + e.getMessage(), e);
            showToast("Unable to create virtual display");
        }
    }
    private void safeCaptureFrame() {
        new Thread(() -> {
            try {
                Image image = imageReader.acquireLatestImage();
                if (image == null) {
                    Log.w("RhineLT", "Failed to acquire image: imageReader returns null");
                    showToast("Failed to acquire image");
                    return;
                }
                final Bitmap bitmap = imageToBitmap(image);
                image.close();
                if (bitmap != null) {
                    mainHandler.post(() -> {
                        try {
                            File file = saveBitmapToFile(bitmap);
                            if (file != null) {
                                Log.d("RhineLT", "Screenshot saved successfully, path:" + file.getAbsolutePath());
                                uploadImageWithRetry(file, 1);
                            }
                        } catch (Exception e) {
                            Log.e("RhineLT", "Processing failed: " + e.getMessage(), e);
                            showToast("Failed to save or upload");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("RhineLT", "Capture failed: " + e.getMessage(), e);
                showToast("Screenshot failed");
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
            Log.d("RhineLT", "Image conversion successful, dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;
        } catch (Exception e) {
            Log.e("RhineLT", "Conversion failed: " + e.getMessage(), e);
            return null;
        }
    }
    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create directory: " + directory.getAbsolutePath());
        }
        File file = new File(directory, "SCREENSHOT_" + timeStamp + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            Log.d("RhineLT", "File saved successfully: " + file.length() + " bytes");
            return file;
        }
    }
    private void uploadImageWithRetry(File file, int maxRetries) {
        new Thread(() -> {
            int retryCount = 0;
            boolean success = false;
            
            while (retryCount < maxRetries && !success) {
                try {
                    Log.d("RhineLT", "Upload attempt (" + (retryCount+1) + ") time)");
                    success = uploadImage(file);
                    if (!success) {
                        Log.w("RhineLT", "Attempted "+(retryCount+1)+" failures");
                        retryCount++;
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    Log.e("RhineLT", "Upload error: " + 
                        e.getClass().getSimpleName() + " - " + e.getMessage());
                    retryCount++;
                }
            }
            if (!success) {
                showToast("上传失败，请检查网络后重试");
                Log.e("RhineLT", "The upload failed due to reaching the maximum number of retries.");
            }
        }).start();
    }
    private boolean uploadImage(File file) throws IOException {
        Log.d("RhineLT", "Ready to upload file: " + file.getAbsolutePath() + 
            ", size: " + file.length() + " bytes");
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

        String urlPrefix = urlEditText.getText().toString().isEmpty() ? "https://47.94.2.169:4680" : urlEditText.getText().toString();
        String url = urlPrefix + "/translate/with-form/image";
        String detector = detectorEditText.getText().toString().isEmpty() ? "default" : detectorEditText.getText().toString();
        String detectionSize = detectionSizeEditText.getText().toString().isEmpty() ? "1536" : detectionSizeEditText.getText().toString();
        String translator = translatorEditText.getText().toString().isEmpty() ? "gpt3.5" : translatorEditText.getText().toString();

        String configJson = "{ \"detector\": { \"detector\": \"" + detector + "\", \"detection_size\": " + detectionSize + " }, \"render\": { \"direction\": \"auto\" }, \"translator\": { \"translator\": \"" + translator + "\", \"target_lang\": \"CHS\" } }";
        
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("config", configJson)
                .addFormDataPart("image", file.getName(), 
                    RequestBody.create(MediaType.parse("image/png"), file))
                .build();
        Log.d("RhineLT", "Build Multipart request body, number of fields:" + body.contentLength() + " bytes");
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("User-Agent", "PostmanRuntime-ApipostRuntime/1.1.0")
                .addHeader("Connection", "keep-alive")
                .addHeader("content-type", "multipart/form-data;")
                .build();

        Log.d("RhineLT", "Initiate request => URL: " + request.url());
        Log.d("RhineLT", "Headers: " + request.headers());
        try (Response response = client.newCall(request).execute()) {
            Log.d("RhineLT", "Response Code: " + response.code());
            Log.d("RhineLT", "Response header: " + response.headers());
            
            if (response.body() != null) {
                byte[] bytes;
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(response.body().byteStream())) {
                    bytes = gzipInputStream.readAllBytes();
                }
                Log.d("RhineLT", "Received response data length:" + bytes.length + " bytes");
                
                if (bytes.length < 100) {
                    String bodyStr = new String(bytes, StandardCharsets.UTF_8);
                    Log.e("RhineLT", "Invalid response content:" + bodyStr);
                    return false;
                }
                
                saveTranslatedImage(bytes, file.getName());
                return true;
            }
            return false;
        } catch (IOException e) {
            Log.e("RhineLT", "Network request error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw e;
        }
    }

    private void saveTranslatedImage(byte[] imageBytes, String originalName) {
        Log.d("RhineLT", "Start saving translated image, original file name:" + originalName + 
            ", Data length: " + imageBytes.length + " bytes");
        
        if (!isValidImage(imageBytes)) {
            Log.e("RhineLT", "Invalid image data");
            showToast("收到无效的图片数据");
            return;
        }
        
        new Thread(() -> {
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "TRANSLATED_" + timeStamp + ".png");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(imageBytes);
                    out.flush();
                    Log.d("RhineLT", "Translation saved successfully: " + file.getAbsolutePath());
                    showToast("保存成功: " + file.getName());
                    
                    // 使用 FileProvider 生成 content URI
                    Uri uri = FileProvider.getUriForFile(this, "com.hjq.window.demo.fileprovider", file);
                    
                    // 调用系统打开图像
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "image/png");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                    
                }
            } catch (Exception e) {
                Log.e("RhineLT", "Failed to save: " + e.getMessage(), e);
                showToast("翻译结果保存失败");
            }
        }).start();
    }

    private boolean isValidImage(byte[] data) {
        try {
            BitmapFactory.decodeByteArray(data, 0, data.length);
            return true;
        } catch (Exception e) {
            Log.e("RhineLT", "Image data parsing failed: " + e.getMessage());
            return false;
        }
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
                Log.d("RhineLT", "ImageReader has been released");
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
                Log.d("RhineLT", "VirtualDisplayhas been released");
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
                Log.d("RhineLT", "MediaProjectionhas been stoped");
            }
        } catch (Exception e) {
            Log.e("RhineLT", "Resource release failed: " + e.getMessage(), e);
        }
    }


    private void showToast(String message) {
        mainHandler.post(() -> Toaster.show(message));
    }

    @Override
    public void onClick(View v) {
        final int viewId = v.getId();
        if (viewId == R.id.btn_main_global) {
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
                        Toaster.show("正在翻译...");
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
