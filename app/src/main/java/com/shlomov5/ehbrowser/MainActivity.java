package com.shlomov5.ehbrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.GravityCompat;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends Activity {
    private final int STORAGE_PERMISSION_CODE = 1;
    private WebView mWebView;
    private Button settingsButton;
    private SharedPreferences sp;
    private List<String> whiteHosts = new ArrayList<>();
    private String domain;
    private static final String PREFS_NAME = "MyPrefsFile";
    private static final String KEY_ACCEPTED = "acceptedTerms";
    private static final String PREF_APPROVED_URLS = "approved_urls";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "MissingInflatedId"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ImageButton menuButton = findViewById(R.id.settings_button);

        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            if (id == R.id.nav_settings) {
                if (PasswordUtils.isSettingsLockEnabled(this)) {
                    promptForPasswordAndOpenSettings();
                } else {
                    openSettingsActivity();
                }
                return true;
            }
            else if (id == R.id.nav_feedback) {
                mWebView.loadUrl("https://docs.google.com/forms/d/e/1FAIpQLScrkV2nmeszXD5kdeyIZT1Z4H3XeRx3r2W59Np_bO72Rjwhxw/viewform?usp=header");
                return true;
            } else if (id == R.id.nav_about) {
                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(intent);
                return true;
            } else if (id == R.id.more_apps) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=%D7%90%D7%A9%D7%99+%D7%95%D7%A8%D7%93"));
                startActivity(intent);
                return true;
            }
            return false;
        });



        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrashLogToFile(throwable);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });

        requestStoragePermission();

        // Terms dialog removed - user requirement

        sp = PreferenceManager.getDefaultSharedPreferences(this);

        // Load approved URLs from preferences
        loadApprovedUrls();

        // WebView Setup (Original)
        mWebView = findViewById(R.id.activity_main_webview);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setPluginState(WebSettings.PluginState.ON);
        webSettings.setAllowFileAccess(false);
        mWebView.setWebViewClient(new HelloWebViewClient());

        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri source = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(source);
            String cookies = CookieManager.getInstance().getCookie(url);
            request.addRequestHeader("cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Downloading File..."); // Original description
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request); // Original direct enqueue
            Toast.makeText(this, R.string.downloading, Toast.LENGTH_LONG).show();
        });

        mWebView.loadUrl("https://www.etzhaim.org.il");


    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission needed")
                        .setMessage("This permission is needed to write log files") // Original message
                        .setPositiveButton("ok", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this,
                                // Original code requested only WRITE here in the dialog callback
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE))
                        .setNegativeButton("cancel", (dialog, which) -> dialog.dismiss())
                        .create().show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            }
        } else {
            writeLogToFile();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Original code called writeLogToFile on success
                writeLogToFile();
            } else {
                Log.e("MainActivity", "Permission denied"); // Original log message
            }
        }
    }

    private void writeLogToFile() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File logFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log.txt");

            try (FileWriter fileWriter = new FileWriter(logFile, true)) {
                Process process = Runtime.getRuntime().exec("logcat -d"); // Original command
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    fileWriter.write(line + "\n");
                }

                fileWriter.flush();

            } catch (IOException e) {
                Log.e("MainActivity", "Error writing log to file", e);
            } catch (SecurityException se) { // Added this catch block based on later versions, good practice.
                Log.e("MainActivity", "Security exception accessing logcat", se);
            }
        } else {
            Log.e("MainActivity", "External storage not available"); // Original message
        }
    }

    private void writeCrashLogToFile(Throwable throwable) {
        // No explicit permission check inside the method in the original code.
        File logFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log.txt");
        try (FileWriter fileWriter = new FileWriter(logFile, true)) {
            fileWriter.write("Crash occurred at: " + System.currentTimeMillis() + "\n"); // Original format
            fileWriter.write("Exception: " + throwable.toString() + "\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                fileWriter.write("    at " + element.toString() + "\n");
            }
            fileWriter.write("\n");
            // Ensure written, good practice.
            fileWriter.flush();
        } catch (IOException e) {
            Log.e("MainActivity", "Error writing crash log to file", e);
        }
    }
    private void promptForPasswordAndOpenSettings() {
        if (!PasswordUtils.isSettingsLockEnabled(this)) {
            openSettingsActivity();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enter_password_title);
        builder.setMessage(R.string.settings_locked);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.password_hint);
        // Add padding using a wrapper layout
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int padding_in_dp = 16;
        final float scale = getResources().getDisplayMetrics().density;
        int padding_in_px = (int) (padding_in_dp * scale + 0.5f);
        lp.setMargins(padding_in_px, padding_in_px, padding_in_px, padding_in_px);
        input.setLayoutParams(lp);
        container.addView(input);
        builder.setView(container);
        builder.setPositiveButton(R.string.enter, null);
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = input.getText().toString();
                if (PasswordUtils.checkPassword(MainActivity.this, password)) {
                    dialog.dismiss();
                    openSettingsActivity();
                } else {
                    input.setError(getString(R.string.incorrect_password));
                }
            });
        });
        dialog.show();
    }


    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void loadApprovedUrls() {
        Set<String> urls = sp.getStringSet(PREF_APPROVED_URLS, getDefaultUrls());
        whiteHosts.clear();
        whiteHosts.addAll(urls);
    }

    private Set<String> getDefaultUrls() {
        Set<String> defaultUrls = new HashSet<>();
        defaultUrls.add("etzhaim.org.il");
        defaultUrls.add("www.etzhaim.org.il");
        defaultUrls.add("wordwall.net");
        defaultUrls.add("www.wordwall.net");
        return defaultUrls;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload approved URLs when returning to MainActivity
        loadApprovedUrls();
    }

    private class HelloWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Boolean photos = sp.getBoolean("photos", false);
            WebSettings webFilters = mWebView.getSettings();
            String host = Uri.parse(url).getHost();
            domain = Uri.parse(url).getHost(); // Original assignment
            if (whiteHosts.contains(host)) {
                if (photos) {
                    webFilters.setLoadsImagesAutomatically(false);
                    return false;
                } else {
                    webFilters.setLoadsImagesAutomatically(true);
                    return false;
                }
            } else {
                blockString();
                return true;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Boolean photosInFinish = sp.getBoolean("photos", false);
            super.onPageFinished(view, url);

            if (photosInFinish) {
                view.loadUrl("javascript:(function() { " +
                        "function processNode(node) { " +
                        "  if (node.nodeType === 1) { /* Element node */ " +
                        "    var tagName = node.tagName.toUpperCase(); " +
                        "    if (tagName === 'IMG') { " +
                        "      if (node.style.visibility !== 'hidden' && (node.width > 32 || node.naturalWidth > 32) && (node.height > 32 || node.naturalHeight > 32)) { " +
                        "        var blankImageUrl = 'data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=='; " +
                        "        var style = window.getComputedStyle(node); " +
                        "        var width = style.width; var height = style.height; "+
                        "        node.src = blankImageUrl; node.srcset = ''; "+
                        "        node.style.visibility = 'hidden'; " +
                        "        node.style.width = width; node.style.height = height; "+
                        "        node.style.background = 'none'; "+
                        "        node.style.backgroundImage = 'none'; "+
                        "      } " +
                        "    } else if (tagName === 'VIDEO' || tagName === 'IFRAME' || tagName === 'OBJECT' || tagName === 'EMBED' || tagName === 'PICTURE') { " +
                        "      node.style.display = 'none'; node.style.visibility = 'hidden'; "+
                        "    } else if (tagName === 'SOURCE' && node.closest('video, audio, picture')) { "+
                        "      node.remove(); "+
                        "    } else if (node.style.backgroundImage && node.style.backgroundImage !== 'none') { "+
                        "       node.style.backgroundImage = 'none'; "+
                        "    }" +
                        "  } " +
                        "} " +
                        "document.querySelectorAll('img, video, iframe, object, embed, picture, source, [style*=\"background-image\"]').forEach(processNode); " +
                        "const observer = new MutationObserver((mutations) => { " +
                        "  mutations.forEach((mutation) => { " +
                        "    mutation.addedNodes.forEach(newNode => { "+
                        "       if (newNode.nodeType === 1) { processNode(newNode); } "+
                        "       if (newNode.querySelectorAll) { newNode.querySelectorAll('img, video, iframe, object, embed, picture, source, [style*=\"background-image\"]').forEach(processNode); } "+
                        "    }); " +
                        "  }); " +
                        "}); " +
                        "observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'srcset', 'style'] }); " +
                        "})();");
            }
        }
    }

    public void blockString() {
        sp = PreferenceManager.getDefaultSharedPreferences(this); // Original reload
        Boolean url = sp.getBoolean("URL", false); // Original variable name
        if (url) {
            Toast.makeText(this, domain + " " + getString(R.string.blocked_page), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.blocked_page, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}