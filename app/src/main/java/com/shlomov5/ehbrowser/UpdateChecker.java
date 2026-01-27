package com.shlomov5.ehbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/ShlomoV5/EtzHaimBrowser/releases/latest";
    private static final String UPDATE_APK_FILENAME = "EtzHaimBrowser-update.apk";
    private Activity activity;
    private long downloadId = -1;
    
    public UpdateChecker(Activity activity) {
        this.activity = activity;
    }
    
    public void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String latestVersion = jsonResponse.getString("tag_name");
                    String downloadUrl = null;
                    
                    // Get the APK download URL from assets
                    if (jsonResponse.has("assets")) {
                        org.json.JSONArray assets = jsonResponse.getJSONArray("assets");
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.getString("name");
                            if (assetName.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url");
                                break;
                            }
                        }
                    }
                    
                    if (isNewerVersion(latestVersion) && downloadUrl != null) {
                        final String finalDownloadUrl = downloadUrl;
                        final String finalVersion = latestVersion;
                        activity.runOnUiThread(() -> showUpdateDialog(finalVersion, finalDownloadUrl));
                    }
                } else {
                    Log.e(TAG, "Failed to check for updates. Response code: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                activity.runOnUiThread(() -> 
                    Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private boolean isNewerVersion(String latestVersion) {
        try {
            // Remove 'v' prefix if present
            String cleanVersion = latestVersion.startsWith("v") ? 
                latestVersion.substring(1) : latestVersion;
            
            PackageInfo packageInfo = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
            String currentVersion = packageInfo.versionName;
            
            // Simple version comparison
            String[] latestParts = cleanVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");
            
            int maxLength = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < maxLength; i++) {
                int latest = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                
                if (latest > current) {
                    return true;
                } else if (latest < current) {
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error comparing versions", e);
            return false;
        }
    }
    
    private void showUpdateDialog(String version, String downloadUrl) {
        String message = activity.getString(R.string.update_message, version);
        
        new AlertDialog.Builder(activity)
            .setTitle(R.string.update_available)
            .setMessage(message)
            .setPositiveButton(R.string.download, (dialog, which) -> downloadUpdate(downloadUrl))
            .setNegativeButton(R.string.later, null)
            .show();
    }
    
    private void downloadUpdate(String downloadUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("EtzHaimBrowser Update");
            request.setDescription(activity.getString(R.string.downloading_update));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, UPDATE_APK_FILENAME);
            
            DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = downloadManager.enqueue(request);
            
            // Register receiver to install APK after download
            BroadcastReceiver onComplete = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (downloadId == id) {
                        installUpdate();
                        context.unregisterReceiver(this);
                    }
                }
            };
            
            activity.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            
            Toast.makeText(activity, R.string.downloading_update, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error downloading update", e);
            Toast.makeText(activity, R.string.error_downloading_update, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void installUpdate() {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), UPDATE_APK_FILENAME);
            
            if (!file.exists()) {
                Toast.makeText(activity, R.string.update_file_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(activity, 
                    activity.getPackageName() + ".provider", file);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            
            activity.startActivity(intent);
            Toast.makeText(activity, R.string.update_downloaded, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error installing update", e);
            Toast.makeText(activity, R.string.error_installing_update, Toast.LENGTH_SHORT).show();
        }
    }
}
