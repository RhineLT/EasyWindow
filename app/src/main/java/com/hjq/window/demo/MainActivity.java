package com.hjq.window.demo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.FileOutputStream;
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

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/EasyWindow
 *    time   : 2019/01/04
 *    desc   : Demo 使用案例
 */
public final class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

    @Override
    public void onBackPressed() {
        // 在某些手机上面无法通过返回键销毁当前 Activity 对象，从而无法触发 LeakCanary 回收对象
        finish();
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
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
                    // 设置外层是否能被触摸
                    .setOutsideTouchable(false)
                    // 设置窗口背景阴影强度
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
                    // 设置成可拖拽的
                    .setDraggable(new MovingDraggable())
                    .setOnClickListener(android.R.id.message, new EasyWindow.OnClickListener<TextView>() {

                        @Override
                        public void onClick(EasyWindow<?> easyWindow, TextView view) {
                            easyWindow.cancel();
                        }
                    })
                    .show();

        } else if (viewId == R.id.btn_main_global) {

            XXPermissions.with(MainActivity.this)
                    .permission(Permission.SYSTEM_ALERT_WINDOW)
                    .request(new OnPermissionCallback() {

                        @Override
                        public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                            // 这里最好要做一下延迟显示，因为在某些手机（华为鸿蒙 3.0）上面立即显示会导致显示效果有一些瑕疵
                            runOnUiThread(() -> showGlobalWindow(getApplication()));
                        }

                        @Override
                        public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                            EasyWindow.with(MainActivity.this)
                                    .setDuration(1000)
                                    .setContentView(R.layout.window_hint)
                                    .setImageDrawable(android.R.id.icon, R.drawable.ic_dialog_tip_error)
                                    .setText(android.R.id.message, "请先授予悬浮窗权限")
                                    .show();
                        }
                    });

        } else if (viewId == R.id.btn_main_cancel_all) {

            // 关闭当前正在显示的悬浮窗
            // EasyWindow.cancelAll();
            // 回收当前正在显示的悬浮窗
            EasyWindow.recycleAllWindow();

        } else if (viewId == R.id.btn_main_utils) {

            EasyWindow.with(this)
                    .setDuration(1000)
                    // 将 Toaster 中的 View 转移给 EasyWindow 来显示
                    .setContentView(Toaster.getStyle().createView(this))
                    .setAnimStyle(R.style.ScaleAnimStyle)
                    .setText(android.R.id.message, "就问你溜不溜")
                    .setGravity(Gravity.BOTTOM)
                    .setYOffset(100)
                    .show();
        }
    }

    /**
     * 显示全局弹窗
     */
    public static void showGlobalWindow(Application application) {
        SpringBackDraggable springBackDraggable = new SpringBackDraggable(SpringBackDraggable.ORIENTATION_HORIZONTAL);
        springBackDraggable.setAllowMoveToScreenNotch(false);
        // 传入 Application 表示这个是一个全局的 Toast
        EasyWindow.with(application)
                .setContentView(R.layout.window_phone)
                .setGravity(Gravity.END | Gravity.BOTTOM)
                .setYOffset(200)
                // 设置指定的拖拽规则
                .setDraggable(springBackDraggable)
                .setOnClickListener(android.R.id.icon, new EasyWindow.OnClickListener<ImageView>() {
    
                    @Override
                    public void onClick(EasyWindow<?> easyWindow, ImageView view) {
                        Toaster.show("我被点击了");
    
                        // 显示步骤1
                        EasyWindow.with(application)
                                .setDuration(1000)
                                .setContentView(R.layout.window_hint)
                                .setText(android.R.id.message, "步骤1：获取当前屏幕截图")
                                .show();
    
                        // 获取当前屏幕截图
                        View rootView = easyWindow.getContentView().getRootView();
                        rootView.setDrawingCacheEnabled(true);
                        Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
                        rootView.setDrawingCacheEnabled(false);
    
                        // 显示步骤2
                        EasyWindow.with(application)
                                .setDuration(1000)
                                .setContentView(R.layout.window_hint)
                                .setText(android.R.id.message, "步骤2：将截图保存到文件")
                                .show();
    
                        // 将截图保存到文件
                        File screenshotFile = new File(application.getCacheDir(), "screenshot.png");
                        try (FileOutputStream fos = new FileOutputStream(screenshotFile)) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
    
                        // 显示步骤3
                        EasyWindow.with(application)
                                .setDuration(1000)
                                .setContentView(R.layout.window_hint)
                                .setText(android.R.id.message, "步骤3：创建上传任务")
                                .show();
    
                        // 创建上传任务
                        new Thread(() -> {
                            try {
                                OkHttpClient client = new OkHttpClient();
    
                                MediaType mediaType = MediaType.parse("multipart/form-data; boundary=---011000010111000001101001");
                                MultipartBody.Builder builder = new MultipartBody.Builder()
                                        .setType(MultipartBody.FORM)
                                        .addFormDataPart("config", "{ \"detector\": { \"detector\": \"default\", \"detection_size\": 1536 }, \"render\": { \"direction\": \"auto\" }, \"translator\": { \"translator\": \"gpt3.5\", \"target_lang\": \"CHS\" } }")
                                        .addFormDataPart("image", "screenshot.png", RequestBody.create(mediaType, screenshotFile));
    
                                RequestBody body = builder.build();
                                Request request = new Request.Builder()
                                        .url("http://47.94.2.169:777/translate/with-form/image")
                                        .post(body)
                                        .addHeader("Accept", "*/*")
                                        .addHeader("Accept-Encoding", "gzip, deflate, br")
                                        .addHeader("User-Agent", "PostmanRuntime-ApipostRuntime/1.1.0")
                                        .addHeader("Connection", "keep-alive")
                                        .addHeader("content-type", "multipart/form-data; boundary=---011000010111000001101001")
                                        .build();
    
                                // 显示步骤4
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    EasyWindow.with(application)
                                            .setDuration(1000)
                                            .setContentView(R.layout.window_hint)
                                            .setText(android.R.id.message, "步骤4：发送请求")
                                            .show();
                                });
    
                                // 记录请求信息
                                Log.d("MainActivity", "发送请求: " + request.toString());
    
                                Response response = client.newCall(request).execute();
                                if (response.isSuccessful()) {
                                    String responseString = response.body().string();
                                    Log.d("MainActivity", "响应成功: " + responseString);
    
                                    String base64Data = responseString.substring(responseString.indexOf("base64,") + 7);
                                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                                    Bitmap responseBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    
                                    // 显示步骤5
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        EasyWindow.with(application)
                                                .setDuration(1000)
                                                .setContentView(R.layout.window_hint)
                                                .setText(android.R.id.message, "步骤5：处理响应")
                                                .show();
                                    });
    
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        ImageView imageView = new ImageView(application);
                                        imageView.setImageBitmap(responseBitmap);
                                        EasyWindow.with(application)
                                                .setContentView(imageView)
                                                .setGravity(Gravity.CENTER)
                                                .show();
                                    });
                                } else {
                                    Log.d("MainActivity", "响应失败: " + response.toString());
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                })
                .setOnLongClickListener(android.R.id.icon, new EasyWindow.OnLongClickListener<View>() {
                    @Override
                    public boolean onLongClick(EasyWindow<?> easyWindow, View view) {
                        Toaster.show("我被长按了");
                        return false;
                    }
                })
                .show();
    }
}