import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.io.File;
import java.io.IOException;

public class CaptureService extends Service {
    private static final String CHANNEL_ID = "capture_channel";
    private static final int NOTIFICATION_ID = 1001;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private File lastProcessedFile;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildServiceNotification());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Translation Service",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕翻译服务")
                .setContentText("正在后台运行")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    public void setupMediaProjection(MediaProjection projection) {
        this.mediaProjection = projection;
        setupVirtualDisplay();
    }

    private void setupVirtualDisplay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "BackgroundCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );
    }

    public void captureAndProcess() {
        new Thread(() -> {
            try {
                Image image = imageReader.acquireLatestImage();
                if (image == null) {
                    Log.w("RhineLT", "Acquire image failed");
                    return;
                }

                Bitmap bitmap = imageToBitmap(image);
                image.close();

                if (bitmap != null) {
                    processImage(bitmap);
                }
            } catch (Exception e) {
                Log.e("RhineLT", "Capture error: " + e.getMessage());
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        // 保持原有转换逻辑
        // ...
    }

    private void processImage(Bitmap bitmap) {
        try {
            File file = saveBitmapToFile(bitmap);
            uploadImageWithRetry(file, 3);
        } catch (IOException e) {
            Log.e("RhineLT", "Save failed: " + e.getMessage());
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

        String configJson = "{ \"detector\": { \"detector\": \"default\", \"detection_size\": 1536 }, \"render\": { \"direction\": \"auto\" }, \"translator\": { \"translator\": \"gpt3.5\", \"target_lang\": \"CHS\" } }";
        
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("config", configJson)
                .addFormDataPart("image", file.getName(), 
                    RequestBody.create(MediaType.parse("image/png"), file))
                .build();
        Log.d("RhineLT", "Build Multipart request body, number of fields:" + body.contentLength() + " bytes");
        Request request = new Request.Builder()
                .url("https://47.94.2.169:4680/translate/with-form/image")
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

    void sendResultNotification(File imageFile) {
        lastProcessedFile = imageFile;
        
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra("image_path", imageFile.getAbsolutePath())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("翻译完成")
                .setContentText("点击查看结果")
                .setSmallIcon(R.drawable.ic_done)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID + 1, notification);
    }

    @Override
    public void onDestroy() {
        releaseResources();
        super.onDestroy();
    }

    private void releaseResources() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
