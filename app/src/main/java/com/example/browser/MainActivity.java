package com.example.browser;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText etUrl;
    private ImageButton btnBack, btnForward, btnRefresh, btnHistory;
    private Button btnGo;
    private ProgressBar progressBar;
    private View statusBarBackground;
    private View toolbar;

    private List<String> historyList;
    private static final String DEFAULT_URL = "https://cn.bing.com";
    private boolean isFullscreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置沉浸式状态栏
        setupImmersiveStatusBar();

        setContentView(R.layout.activity_main);

        initViews();
        setupStatusBar();
        initWebView();
        setupClickListeners();

        historyList = new ArrayList<>();

        // 加载默认网页
        loadUrl(DEFAULT_URL);
    }

    private void setupImmersiveStatusBar() {
        Window window = getWindow();

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

        window.getDecorView().setSystemUiVisibility(visibility);
    }


    private void initViews() {
        webView = findViewById(R.id.webView);
        etUrl = findViewById(R.id.etUrl);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHistory = findViewById(R.id.btnHistory);
        btnGo = findViewById(R.id.btnGo);
        progressBar = findViewById(R.id.progressBar);
        statusBarBackground = findViewById(R.id.statusBarBackground);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupStatusBar() {
        // 设置状态栏高度
        int statusBarHeight = getStatusBarHeight();
//        if (statusBarHeight > 0) {
//            android.view.ViewGroup.LayoutParams params = statusBarBackground.getLayoutParams();
//            params.height = statusBarHeight;
//            statusBarBackground.setLayoutParams(params);
//
//            // 为工具栏添加状态栏高度的padding，防止内容被状态栏遮挡
//            toolbar.setPadding(
//                    toolbar.getPaddingLeft(),
//                    toolbar.getPaddingTop() + statusBarHeight,
//                    toolbar.getPaddingRight(),
//                    toolbar.getPaddingBottom()
//            );
//        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        // 启用JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // 启用DOM存储
        webView.getSettings().setDomStorageEnabled(true);

        // 设置缓存策略
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        // 支持缩放
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // 设置WebViewClient，使网页在WebView中打开而不是默认浏览器
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                etUrl.setText(url);

                // 添加到历史记录（去重）
                if (!historyList.contains(url)) {
                    historyList.add(url);
                }

                // 更新前进后退按钮状态
                updateNavigationButtons();
            }
        });

        // 设置WebChromeClient，处理进度条等
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                    progressBar.setProgress(0);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && !title.isEmpty()) {
                    // 设置Activity标题
                    setTitle("浏览器 - " + title);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // 进入全屏模式（如播放视频）
                enterFullscreen();
                isFullscreen = true;
            }

            @Override
            public void onHideCustomView() {
                // 退出全屏模式
                exitFullscreen();
                isFullscreen = false;
            }
        });
    }

    private void enterFullscreen() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(uiOptions);

        // 隐藏工具栏
        toolbar.setVisibility(View.GONE);
        statusBarBackground.setVisibility(View.GONE);
    }

    private void exitFullscreen() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(uiOptions);

        // 显示工具栏
        toolbar.setVisibility(View.VISIBLE);
        statusBarBackground.setVisibility(View.VISIBLE);
    }

    private void setupClickListeners() {
        btnGo.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                loadUrl(url);
            } else {
                Toast.makeText(MainActivity.this, "请输入网址", Toast.LENGTH_SHORT).show();
            }
        });

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                Toast.makeText(MainActivity.this, "没有上一页", Toast.LENGTH_SHORT).show();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            } else {
                Toast.makeText(MainActivity.this, "没有下一页", Toast.LENGTH_SHORT).show();
            }
        });

        btnRefresh.setOnClickListener(v -> webView.reload());

        btnHistory.setOnClickListener(v -> showHistoryDialog());

        // 地址栏回车键监听
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            btnGo.performClick();
            return true;
        });
    }

    private void loadUrl(String url) {
        webView.loadUrl(url);
        etUrl.setText(url);

        // 隐藏软键盘
        hideKeyboard();
    }

    private void hideKeyboard() {
        if (getCurrentFocus() != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void updateNavigationButtons() {
        btnBack.setEnabled(webView.canGoBack());
        btnForward.setEnabled(webView.canGoForward());

        // 根据状态改变按钮透明度
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.5f);
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.5f);
    }

    private void showHistoryDialog() {
        if (historyList.isEmpty()) {
            Toast.makeText(this, "历史记录为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 反转历史记录，最新的在最上面
        List<String> reversedHistory = new ArrayList<>(historyList);
        java.util.Collections.reverse(reversedHistory);

        final String[] historyArray = reversedHistory.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("浏览历史 (" + historyList.size() + "条)");

        builder.setItems(historyArray, (dialog, which) -> {
            String selectedUrl = historyArray[which];
            loadUrl(selectedUrl);
        });

        builder.setPositiveButton("清空历史", (dialog, which) -> {
            historyList.clear();
            Toast.makeText(MainActivity.this, "历史记录已清空", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 处理系统UI可见性变化
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullscreen) {
            enterFullscreen();
        }
    }

    // 处理返回键，如果WebView可以返回则返回上一页
    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            exitFullscreen();
            isFullscreen = false;
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            // 显示确认退出对话框
            new AlertDialog.Builder(this)
                    .setTitle("退出浏览器")
                    .setMessage("确定要退出浏览器吗？")
                    .setPositiveButton("确定", (dialog, which) -> finish())
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}