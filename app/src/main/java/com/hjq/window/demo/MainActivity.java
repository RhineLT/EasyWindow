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
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import androidx.appcompat.app.AlertDialog;


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
    private EditText folderPathEditText;
    private TextView detectedImagesTextView;
    private TextView translatedImagesTextView;
    private AtomicInteger detectedImagesCount = new AtomicInteger(0);
    private AtomicInteger translatedImagesCount = new AtomicInteger(0);
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
        folderPathEditText = findViewById(R.id.et_folder_path);
        detectedImagesTextView = findViewById(R.id.tv_detected_images);
        translatedImagesTextView = findViewById(R.id.tv_translated_images);
        findViewById(R.id.btn_local_translate).setOnClickListener(this);
        folderPathEditText.setText("/storage/emulated/0/Download/ManGa_Translate/");
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
                                uploadImageWithRetry(file, 1, true);
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
    private void uploadImageWithRetry(File file, int maxRetries, boolean pictureView) {
        new Thread(() -> {
            int retryCount = 0;
            boolean success = false;
            
            while (retryCount < maxRetries && !success) {
                try {
                    Log.d("RhineLT", "Upload attempt (" + (retryCount+1) + ") time)");
                    success = uploadImage(file, pictureView);
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
    
    private boolean uploadImage(File file, boolean pictureView) throws IOException {
        Log.d("RhineLT", "Ready to upload file: " + file.getAbsolutePath() + 
            ", size: " + file.length() + " bytes");
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

        String urlPrefix = urlEditText.getText().toString().isEmpty() ? "http://10.243.3.100:7500" : urlEditText.getText().toString();
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
                if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) {
                    try (GZIPInputStream gzipInputStream = new GZIPInputStream(response.body().byteStream())) {
                        bytes = gzipInputStream.readAllBytes();
                    }
                } else {
                    bytes = response.body().bytes();
                }
                Log.d("RhineLT", "Received response data length:" + bytes.length + " bytes");
                
                if (bytes.length < 100) {
                    String bodyStr = new String(bytes, StandardCharsets.UTF_8);
                    Log.e("RhineLT", "Invalid response content:" + bodyStr);
                    return false;
                }
                
                saveTranslatedImage(bytes, file.getName(), pictureView);
                return true;
            }
            return false;
        } catch (IOException e) {
            Log.e("RhineLT", "Network request error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw e;
        }
    }

private void saveTranslatedImage(byte[] imageBytes, String originalName, boolean pictureView) {
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
            File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Unable to create directory: " + directory.getAbsolutePath());
            }
            File file = new File(directory, "TRANSLATED_" + originalName + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(imageBytes);
                out.flush();
                Log.d("RhineLT", "Translation saved successfully: " + file.getAbsolutePath());
                
                if (pictureView) {
                    // 使用 FileProvider 生成 content URI
                    Uri uri = FileProvider.getUriForFile(this, "com.hjq.window.demo.fileprovider", file);
                    
                    // 调用系统打开图像
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "image/png");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } else {
                    // 将翻译后的图像保存到指定的本地目录
                    String folderPath = folderPathEditText.getText().toString();
                    File localDirectory = new File(folderPath);
                    if (!localDirectory.exists() && !localDirectory.mkdirs()) {
                        throw new IOException("Unable to create local directory: " + localDirectory.getAbsolutePath());
                    }
                    File localFile = new File(localDirectory, "TRANSLATED_" + originalName + ".png");
                    try (FileOutputStream localOut = new FileOutputStream(localFile)) {
                        localOut.write(imageBytes);
                        localOut.flush();
                        Log.d("RhineLT", "Translation saved to local directory: " + localFile.getAbsolutePath());
                    }
                }
                
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
        isMonitoring = false;
        if (monitoringThread != null) {
            monitoringThread.interrupt();
        }
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
        if (viewId == R.id.btn_local_translate) {
            String folderPath = folderPathEditText.getText().toString();
            if (!folderPath.isEmpty()) {
                // 显示弹窗提示
                new AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("开始监控文件夹变化")
                        .setPositiveButton("确定", (dialog, which) -> {
                            monitorFolderForImages(folderPath);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                showToast("请指定文件夹路径");
            }
        }
    }


    private final Set<String> processedFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean isMonitoring = false;
    private Thread monitoringThread;


    private void monitorFolderForImages(String folderPath) {
        isMonitoring = true;
        monitoringThread = new Thread(() -> {
            try {
                Path path = Paths.get(folderPath);
                WatchService watchService = FileSystems.getDefault().newWatchService();
                path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

                Log.d("RhineLT", "开始监控文件夹: " + folderPath);
                
                while (isMonitoring) {
                    WatchKey key;
                    try {
                        key = watchService.poll(500, TimeUnit.MILLISECONDS); // 添加超时避免阻塞
                        if (key == null) continue;
                    } catch (InterruptedException e) {
                        Log.d("RhineLT", "监控线程被中断");
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        handleFileEvent(event, path);
                    }

                    if (!key.reset()) {
                        Log.e("RhineLT", "WatchKey 失效，重新注册监控");
                        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                    }
                }
            } catch (Exception e) {
                Log.e("RhineLT", "文件夹监控异常: " + e.getMessage(), e);
                showToast("监控异常，请检查文件夹权限");
            } finally {
                Log.d("RhineLT", "停止监控文件夹: " + folderPath);
            }
        });
        monitoringThread.start();
    }

    private void handleFileEvent(WatchEvent<?> event, Path dir) {
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            Path filePath = dir.resolve((Path) event.context());
            String absPath = filePath.toAbsolutePath().toString();
            
            // 跳过已处理文件和翻译结果文件
            if (absPath.contains("TRANSLATED_") || !isSupportedImageFile(absPath)) {
                return;
            }

            if (processedFiles.add(absPath)) {
                Log.d("RhineLT", "检测到新文件: " + filePath);
                handleNewFile(filePath.toFile());
            }
        }
    }

    private boolean isSupportedImageFile(String filePath) {
        String lowerCasePath = filePath.toLowerCase();
        return lowerCasePath.endsWith(".png") || 
            lowerCasePath.endsWith(".jpg") || 
            lowerCasePath.endsWith(".jpeg") || 
            lowerCasePath.endsWith(".bmp") ||
            lowerCasePath.endsWith(".webp") ||  
            lowerCasePath.endsWith(".gif");
    }

    private void handleNewFile(File newFile) {
        new Thread(() -> {
            try {
                waitForFileReady(newFile);
                
                Log.d("RhineLT", "开始处理文件: " + newFile.getName());
                detectedImagesCount.incrementAndGet();
                mainHandler.post(() -> detectedImagesTextView.setText("已检测到 " + detectedImagesCount.get() + " 个图像"));
                
                uploadImageWithRetry(newFile, 1, false);
            } catch (Exception e) {
                Log.e("RhineLT", "文件处理失败: " + newFile.getName(), e);
            }
        }).start();
    }
    
    private void waitForFileReady(File file) throws Exception {
        long timeout = System.currentTimeMillis() + 10000; // 10秒超时
        long lastSize;
        do {
            lastSize = file.length();
            Thread.sleep(500);
        } while (file.length() != lastSize && System.currentTimeMillis() < timeout);

        if (!file.exists() || file.length() == 0) {
            throw new IOException("文件未就绪: " + file.getAbsolutePath());
        }
        Log.d("RhineLT", "文件已就绪: " + file.length() + " bytes");
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
