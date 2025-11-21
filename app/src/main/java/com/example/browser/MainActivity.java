package com.example.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText etUrl;
    private ImageButton btnBack, btnForward, btnRefresh, btnMenu;
    private Button btnGo;
    private ProgressBar progressBar;
    private View statusBarBackground;
    private View toolbar;

    private List<HistoryItem> historyList;
    private List<Bookmark> cloudBookmarks;
    private List<Bookmark> favoritesList;
    private static final String DEFAULT_URL = "https://cn.bing.com";
    private boolean isFullscreen = false;

    // 服务器配置 - 请替换为你的实际服务器地址
    private static final String SERVER_BASE_URL = "https://your-server.com/api";
    private static final String SYNC_TOKEN = "your_sync_token"; // 用户同步令牌

    // 线程池和网络客户端
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 文件存储路径
    private static final String HISTORY_FILE = "browser_history.json";
    private static final String FAVORITES_FILE = "browser_favorites.json";

    // 历史记录项类
    private static class HistoryItem {
        String url;
        String title;
        long timestamp;

        HistoryItem(String url, String title, long timestamp) {
            this.url = url;
            this.title = title;
            this.timestamp = timestamp;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("url", url);
            json.put("title", title);
            json.put("timestamp", timestamp);
            return json;
        }

        static HistoryItem fromJson(JSONObject json) throws JSONException {
            String url = json.getString("url");
            String title = json.getString("title");
            long timestamp = json.getLong("timestamp");
            return new HistoryItem(url, title, timestamp);
        }
    }

    // 书签类
    private static class Bookmark {
        String id;
        String title;
        String url;
        long timestamp;

        Bookmark(String id, String title, String url, long timestamp) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }

        Bookmark(String title, String url) {
            this(null, title, url, System.currentTimeMillis());
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            if (id != null) json.put("id", id);
            json.put("title", title);
            json.put("url", url);
            json.put("timestamp", timestamp);
            return json;
        }

        static Bookmark fromJson(JSONObject json) throws JSONException {
            String id = json.has("id") ? json.getString("id") : null;
            String title = json.getString("title");
            String url = json.getString("url");
            long timestamp = json.getLong("timestamp");
            return new Bookmark(id, title, url, timestamp);
        }

        @Override
        public String toString() {
            return title;
        }
    }

    // 历史记录适配器
    private class HistoryAdapter extends BaseAdapter implements Filterable {
        private Context context;
        private List<HistoryItem> originalList;
        private List<HistoryItem> filteredList;

        public HistoryAdapter(Context context, List<HistoryItem> list) {
            this.context = context;
            this.originalList = new ArrayList<>(list);
            this.filteredList = new ArrayList<>(list);
        }

        @Override
        public int getCount() {
            return filteredList.size();
        }

        @Override
        public HistoryItem getItem(int position) {
            return filteredList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_history, parent, false);
                holder = new ViewHolder();
                holder.tvTitle = convertView.findViewById(R.id.tvTitle);
                holder.tvUrl = convertView.findViewById(R.id.tvUrl);
                holder.tvTime = convertView.findViewById(R.id.tvTime);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            HistoryItem item = getItem(position);
            holder.tvTitle.setText(item.title);
            holder.tvUrl.setText(item.url);

            // 格式化时间
            String time = formatTime(item.timestamp);
            holder.tvTime.setText(time);

            return convertView;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    List<HistoryItem> filtered = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        filtered.addAll(originalList);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (HistoryItem item : originalList) {
                            if (item.title.toLowerCase().contains(filterPattern) ||
                                    item.url.toLowerCase().contains(filterPattern)) {
                                filtered.add(item);
                            }
                        }
                    }

                    results.values = filtered;
                    results.count = filtered.size();
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList = (List<HistoryItem>) results.values;
                    notifyDataSetChanged();
                }
            };
        }

        private class ViewHolder {
            TextView tvTitle;
            TextView tvUrl;
            TextView tvTime;
        }
    }

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
        initData();

        // 加载默认网页
        loadUrl(DEFAULT_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        // 恢复时同步云书签
        syncCloudBookmarks();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        // 暂停时保存数据
        saveHistoryToFile();
        saveFavoritesToFile();
    }

    private void initData() {
        // 从文件加载历史记录和收藏夹
        loadHistoryFromFile();
        loadFavoritesFromFile();

        cloudBookmarks = new ArrayList<>();

        // 异步加载云书签
        syncCloudBookmarks();
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
        btnMenu = findViewById(R.id.btnMenu);
        btnGo = findViewById(R.id.btnGo);
        progressBar = findViewById(R.id.progressBar);
        statusBarBackground = findViewById(R.id.statusBarBackground);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupStatusBar() {
        // 设置状态栏高度
        int statusBarHeight = getStatusBarHeight();
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

                String title = view.getTitle();
                if (title == null || title.isEmpty()) {
                    title = "未命名网页";
                }

                // 添加到历史记录（去重）
                boolean exists = false;
                for (HistoryItem item : historyList) {
                    if (item.url.equals(url)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    historyList.add(new HistoryItem(url, title, System.currentTimeMillis()));
                    // 异步保存历史记录
                    executorService.execute(() -> saveHistoryToFile());
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

    // ========================= 文件存储操作 =========================

    /**
     * 从文件加载历史记录
     */
    private void loadHistoryFromFile() {
        executorService.execute(() -> {
            try {
                FileInputStream fis = openFileInput(HISTORY_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(content.toString());
                List<HistoryItem> loadedHistory = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    loadedHistory.add(HistoryItem.fromJson(jsonArray.getJSONObject(i)));
                }

                mainHandler.post(() -> {
                    historyList = loadedHistory;
                    Log.d("Browser", "Loaded " + historyList.size() + " history items");
                });
            } catch (Exception e) {
                // 文件不存在或其他错误，初始化空列表
                mainHandler.post(() -> {
                    historyList = new ArrayList<>();
                    Log.d("Browser", "Created new history list");
                });
            }
        });
    }

    /**
     * 保存历史记录到文件
     */
    private void saveHistoryToFile() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (HistoryItem item : historyList) {
                jsonArray.put(item.toJson());
            }

            FileOutputStream fos = openFileOutput(HISTORY_FILE, Context.MODE_PRIVATE);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(jsonArray.toString());
            writer.close();

            Log.d("Browser", "Saved " + historyList.size() + " history items");
        } catch (Exception e) {
            Log.e("Browser", "Failed to save history", e);
        }
    }

    /**
     * 从文件加载收藏夹
     */
    private void loadFavoritesFromFile() {
        executorService.execute(() -> {
            try {
                FileInputStream fis = openFileInput(FAVORITES_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(content.toString());
                List<Bookmark> loadedFavorites = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    loadedFavorites.add(Bookmark.fromJson(jsonArray.getJSONObject(i)));
                }

                mainHandler.post(() -> {
                    favoritesList = loadedFavorites;
                    Log.d("Browser", "Loaded " + favoritesList.size() + " favorites");
                });
            } catch (Exception e) {
                // 文件不存在或其他错误，初始化空列表并添加示例数据
                mainHandler.post(() -> {
                    favoritesList = new ArrayList<>();
                    // 添加一些示例收藏夹
                    favoritesList.add(new Bookmark("必应", "https://cn.bing.com"));
                    favoritesList.add(new Bookmark("百度", "https://www.baidu.com"));
                    Log.d("Browser", "Created new favorites list with examples");
                });
            }
        });
    }

    /**
     * 保存收藏夹到文件
     */
    private void saveFavoritesToFile() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Bookmark bookmark : favoritesList) {
                jsonArray.put(bookmark.toJson());
            }

            FileOutputStream fos = openFileOutput(FAVORITES_FILE, Context.MODE_PRIVATE);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(jsonArray.toString());
            writer.close();

            Log.d("Browser", "Saved " + favoritesList.size() + " favorites");
        } catch (Exception e) {
            Log.e("Browser", "Failed to save favorites", e);
        }
    }

    // ========================= 云书签同步 =========================

    /**
     * 同步云书签
     */
    private void syncCloudBookmarks() {
        executorService.execute(() -> {
            try {
                // 从服务器获取书签
                List<Bookmark> serverBookmarks = fetchBookmarksFromServer();

                // 上传本地更改到服务器（如果有）
                uploadLocalChangesToServer();

                mainHandler.post(() -> {
                    cloudBookmarks.clear();
                    cloudBookmarks.addAll(serverBookmarks);
                    Toast.makeText(MainActivity.this,
                            "已同步 " + cloudBookmarks.size() + " 个云书签",
                            Toast.LENGTH_SHORT).show();
                    Log.d("Browser", "Synced " + cloudBookmarks.size() + " cloud bookmarks");
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this,
                            "同步失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e("Browser", "Sync failed", e);
                });
            }
        });
    }

    /**
     * 从服务器获取书签
     */
    private List<Bookmark> fetchBookmarksFromServer() throws IOException, JSONException {
        // 这里替换为你的实际服务器API
        String url = SERVER_BASE_URL + "/bookmarks?token=" + SYNC_TOKEN;

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("服务器错误: " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (jsonResponse.getInt("code") != 200) {
                throw new IOException("API错误: " + jsonResponse.getString("message"));
            }

            JSONArray bookmarksArray = jsonResponse.getJSONArray("data");
            List<Bookmark> bookmarks = new ArrayList<>();

            for (int i = 0; i < bookmarksArray.length(); i++) {
                JSONObject bookmarkJson = bookmarksArray.getJSONObject(i);
                Bookmark bookmark = new Bookmark(
                        bookmarkJson.getString("id"),
                        bookmarkJson.getString("title"),
                        bookmarkJson.getString("url"),
                        bookmarkJson.getLong("created_at")
                );
                bookmarks.add(bookmark);
            }

            return bookmarks;
        }
    }

    /**
     * 上传本地更改到服务器
     */
    private void uploadLocalChangesToServer() throws IOException, JSONException {
        // 这里可以添加检测本地更改并上传的逻辑
        // 例如：新添加的收藏夹、修改的书签等

        // 示例：上传所有收藏夹到服务器
        JSONArray uploadArray = new JSONArray();
        for (Bookmark bookmark : favoritesList) {
            uploadArray.put(bookmark.toJson());
        }

        // 实际实现中，这里应该发送POST请求到服务器
        Log.d("Browser", "Would upload " + uploadArray.length() + " items to server");
    }

    /**
     * 添加书签到云服务器
     */
    private void addBookmarkToServer(Bookmark bookmark) {
        executorService.execute(() -> {
            try {
                // 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("token", SYNC_TOKEN);
                requestBody.put("title", bookmark.title);
                requestBody.put("url", bookmark.url);

                // 这里应该发送POST请求到服务器
                // 示例代码，实际需要实现网络请求
                Log.d("Browser", "Would add bookmark to server: " + bookmark.title);

                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "已添加到云书签", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "添加到云书签失败", Toast.LENGTH_SHORT).show();
                    Log.e("Browser", "Failed to add bookmark to server", e);
                });
            }
        });
    }

    // ========================= UI 操作 =========================

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

        // 菜单按钮点击事件
        btnMenu.setOnClickListener(v -> showMenu());

        // 地址栏回车键监听
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            btnGo.performClick();
            return true;
        });
    }

    private void showMenu() {
        PopupMenu popupMenu = new PopupMenu(this, btnMenu);
        popupMenu.getMenuInflater().inflate(R.menu.browser_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_history) {
                showHistoryDialog();
                return true;
            } else if (id == R.id.menu_cloud_bookmarks) {
                showCloudBookmarksDialog();
                return true;
            } else if (id == R.id.menu_favorites) {
                showFavoritesDialog();
                return true;
            } else if (id == R.id.menu_add_favorite) {
                addCurrentToFavorites();
                return true;
            } else if (id == R.id.menu_sync) {
                syncCloudBookmarks();
                return true;
            } else if (id == R.id.menu_add_cloud) {
                addCurrentToCloudBookmarks();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showHistoryDialog() {
        if (historyList.isEmpty()) {
            Toast.makeText(this, "历史记录为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建自定义对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_history, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();

        // 获取视图组件
        TextView tvHistoryCount = dialogView.findViewById(R.id.tvHistoryCount);
        EditText etSearch = dialogView.findViewById(R.id.etSearch);
        ListView listViewHistory = dialogView.findViewById(R.id.listViewHistory);
        Button btnClearHistory = dialogView.findViewById(R.id.btnClearHistory);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        // 设置历史记录数量
        tvHistoryCount.setText(historyList.size() + "条记录");

        // 创建适配器
        HistoryAdapter adapter = new HistoryAdapter(this, historyList);
        listViewHistory.setAdapter(adapter);

        // 设置列表项点击事件
        listViewHistory.setOnItemClickListener((parent, view, position, id) -> {
            HistoryItem selectedItem = adapter.getItem(position);
            loadUrl(selectedItem.url);
            dialog.dismiss();
        });

        // 设置长按事件（删除单条记录）
        listViewHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            HistoryItem item = adapter.getItem(position);
            new AlertDialog.Builder(this)
                    .setTitle("删除记录")
                    .setMessage("确定要删除这条历史记录吗？\n" + item.title)
                    .setPositiveButton("删除", (d, which) -> {
                        historyList.remove(position);
                        adapter.notifyDataSetChanged();
                        tvHistoryCount.setText(historyList.size() + "条记录");
                        saveHistoryToFile();
                        Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();

                        // 如果删除后列表为空，关闭对话框
                        if (historyList.isEmpty()) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        // 搜索功能
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 清空历史按钮
        btnClearHistory.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清空历史记录")
                    .setMessage("确定要清空所有历史记录吗？此操作不可恢复。")
                    .setPositiveButton("清空", (d, which) -> {
                        historyList.clear();
                        adapter.notifyDataSetChanged();
                        tvHistoryCount.setText("0条记录");
                        saveHistoryToFile();
                        Toast.makeText(MainActivity.this, "历史记录已清空", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 关闭按钮
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 显示对话框
        dialog.show();

        // 设置对话框窗口属性
        if (dialog.getWindow() != null) {
            // 设置对话框为全屏
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void showCloudBookmarksDialog() {
        if (cloudBookmarks.isEmpty()) {
            Toast.makeText(this, "云书签为空", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] bookmarkTitles = new String[cloudBookmarks.size()];
        for (int i = 0; i < cloudBookmarks.size(); i++) {
            bookmarkTitles[i] = cloudBookmarks.get(i).title;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("云书签 (" + cloudBookmarks.size() + "个)");

        builder.setItems(bookmarkTitles, (dialog, which) -> {
            String selectedUrl = cloudBookmarks.get(which).url;
            loadUrl(selectedUrl);
        });

        builder.setPositiveButton("同步", (dialog, which) -> {
            syncCloudBookmarks();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showFavoritesDialog() {
        if (favoritesList.isEmpty()) {
            Toast.makeText(this, "收藏夹为空", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] favoriteTitles = new String[favoritesList.size()];
        for (int i = 0; i < favoritesList.size(); i++) {
            favoriteTitles[i] = favoritesList.get(i).title;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("收藏夹 (" + favoritesList.size() + "个)");

        builder.setItems(favoriteTitles, (dialog, which) -> {
            String selectedUrl = favoritesList.get(which).url;
            loadUrl(selectedUrl);
        });

        builder.setPositiveButton("管理收藏", (dialog, which) -> {
            showManageFavoritesDialog();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void addCurrentToFavorites() {
        String currentUrl = webView.getUrl();
        String currentTitle = webView.getTitle();

        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "当前没有加载网页", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否已经收藏
        for (Bookmark bookmark : favoritesList) {
            if (bookmark.url.equals(currentUrl)) {
                Toast.makeText(this, "该网页已在收藏夹中", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 添加到收藏夹
        String title = currentTitle != null ? currentTitle : "未命名网页";
        favoritesList.add(new Bookmark(title, currentUrl));
        saveFavoritesToFile();
        Toast.makeText(this, "已添加到收藏夹", Toast.LENGTH_SHORT).show();
    }

    private void addCurrentToCloudBookmarks() {
        String currentUrl = webView.getUrl();
        String currentTitle = webView.getTitle();

        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "当前没有加载网页", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = currentTitle != null ? currentTitle : "未命名网页";
        Bookmark bookmark = new Bookmark(title, currentUrl);

        // 添加到云书签
        addBookmarkToServer(bookmark);
    }

    private void showManageFavoritesDialog() {
        if (favoritesList.isEmpty()) {
            Toast.makeText(this, "收藏夹为空", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] favoriteTitles = new String[favoritesList.size()];
        for (int i = 0; i < favoritesList.size(); i++) {
            favoriteTitles[i] = favoritesList.get(i).title + "\n" + favoritesList.get(i).url;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("管理收藏夹");

        builder.setItems(favoriteTitles, (dialog, which) -> {
            // 这里可以添加编辑或删除收藏的逻辑
            String selectedUrl = favoritesList.get(which).url;
            loadUrl(selectedUrl);
        });

        builder.setPositiveButton("删除选中", (dialog, which) -> {
            // 这里可以添加批量删除逻辑
            Toast.makeText(MainActivity.this, "删除功能开发中", Toast.LENGTH_SHORT).show();
        });

        builder.setNeutralButton("导出收藏", (dialog, which) -> {
            Toast.makeText(MainActivity.this, "导出功能开发中", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
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

    // 格式化时间的方法
    private String formatTime(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "未知时间";
        }
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
    protected void onDestroy() {
        super.onDestroy();
        // 关闭线程池
        executorService.shutdown();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }
}