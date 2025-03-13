package com.hjq.window.demo;

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
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.hjq.toast.Toaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class ScreenCaptureService extends Service {
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startScreenCapture();
    }

    private void startScreenCapture() {
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        captureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(captureIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void safeCaptureFrame() {
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
                                uploadImageWithRetry(file, 3);
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
                    Log.d("RhineLT", "Upload attempt (" + (retryCount + 1) + ") time)");
                    success = uploadImage(file);
                    if (!success) {
                        Log.w("RhineLT", "Attempted " + (retryCount + 1) + " failures");
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

    private void showToast(String message) {
        mainHandler.post(() -> Toaster.show(message));
    }

    public class LocalBinder extends Binder {
        public ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }
}
