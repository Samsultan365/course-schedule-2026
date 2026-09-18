package com.samsultan365.courseschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import com.samsultan365.courseschedule.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://samsultan365.github.io/course-schedule-2026/?app=android";
    private static final String RELEASES_API = "https://api.github.com/repos/Samsultan365/course-schedule-2026/releases/latest";
    private static final String TAG_PREFIX = "android-v";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " CourseScheduleApp/" + BuildConfig.VERSION_NAME);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.clearCache(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("samsultan365.github.io".equals(host) || "appassets.androidplatform.net".equals(host)) {
                    return false;
                }
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadUrl("file:///android_asset/www/index.html");
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }

        checkForUpdate();
        ReminderManager.ensurePermissions(this);
        ReminderManager.sync(this);
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                String json = httpGet(RELEASES_API);
                JSONObject release = new JSONObject(json);
                String tag = release.optString("tag_name", "");
                String latestVersion = tag.startsWith(TAG_PREFIX) ? tag.substring(TAG_PREFIX.length()) : "0";
                if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) <= 0) {
                    return;
                }
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
                if (apkUrl == null || apkUrl.isEmpty()) {
                    return;
                }
                String notes = release.optString("body", "");
                String targetUrl = apkUrl;
                runOnUiThread(() -> showUpdateDialog(latestVersion, notes, targetUrl));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void showUpdateDialog(String latestVersion, String notes, String apkUrl) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("发现新版本 " + latestVersion)
                .setMessage(notes == null || notes.trim().isEmpty() ? "课程表有新版本，是否立即更新？" : notes)
                .setPositiveButton("立即更新", (d, w) -> downloadAndInstall(latestVersion, apkUrl))
                .setNegativeButton("稍后", null)
                .create();
        dialog.show();
    }

    private void downloadAndInstall(String latestVersion, String apkUrl) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("正在下载新版本...");
        progress.show();

        new Thread(() -> {
            File file = null;
            try {
                File dir = getExternalFilesDir("updates");
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                file = new File(dir, "course-schedule-" + latestVersion + ".apk");
                downloadFile(apkUrl, file);
                File target = file;
                runOnUiThread(() -> {
                    progress.dismiss();
                    installApk(target);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("更新失败")
                            .setMessage("下载新版本失败，请稍后重试。")
                            .setPositiveButton("知道了", null)
                            .show();
                });
            }
        }).start();
    }

    private void installApk(File file) {
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            openInstallPermissionSettings();
        }
    }

    private void openInstallPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("需要开启安装权限")
                    .setMessage("请在系统设置中允许“课程表”安装未知应用。")
                    .setPositiveButton("知道了", null)
                    .show();
        }
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "CourseSchedule-App");
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        connection.disconnect();
        return sb.toString();
    }

    private void downloadFile(String urlString, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "CourseSchedule-App");
        InputStream in = new BufferedInputStream(connection.getInputStream());
        FileOutputStream out = new FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
        out.close();
        in.close();
        connection.disconnect();
    }

    private int compareVersions(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int x = i < left.length ? parseNumber(left[i]) : 0;
            int y = i < right.length ? parseNumber(right[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private int parseNumber(String value) {
        try {
            return Integer.parseInt(value.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }
}