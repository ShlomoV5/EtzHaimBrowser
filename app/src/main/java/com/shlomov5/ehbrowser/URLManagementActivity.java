package com.shlomov5.ehbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask; // Note: Deprecated in API 30, but still functional. Consider migrating to ExecutorService or Coroutines for future versions.
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class URLManagementActivity extends Activity {

    private static final String PREF_APPROVED_URLS = "approved_urls";
    private ListView urlListView;
    private ArrayAdapter<String> adapter;
    private List<String> urlList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_url_management);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        urlListView = findViewById(R.id.url_list_view);
        Button btnAddUrl = findViewById(R.id.btn_add_url);
        Button btnImportUrls = findViewById(R.id.btn_import_urls);

        // Load approved URLs
        loadUrls();

        // Setup adapter
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, urlList);
        urlListView.setAdapter(adapter);

        // Add URL button
        btnAddUrl.setOnClickListener(v -> showAddUrlDialog());

        // Import URLs button
        btnImportUrls.setOnClickListener(v -> showImportUrlDialog());

        // Long press to remove URL
        urlListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String url = urlList.get(position);
            showRemoveUrlDialog(url, position);
            return true;
        });
    }

    private void loadUrls() {
        Set<String> urls = prefs.getStringSet(PREF_APPROVED_URLS, getDefaultUrls());
        urlList = new ArrayList<>(urls);
    }

    private Set<String> getDefaultUrls() {
        Set<String> defaultUrls = new HashSet<>();
        defaultUrls.add("etzhaim.org.il");
        defaultUrls.add("www.etzhaim.org.il");
        defaultUrls.add("wordwall.net");
        defaultUrls.add("www.wordwall.net");
        return defaultUrls;
    }

    private void saveUrls() {
        Set<String> urls = new HashSet<>(urlList);
        prefs.edit().putStringSet(PREF_APPROVED_URLS, urls).apply();
    }

    private void showAddUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_url);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.enter_url);

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

        builder.setPositiveButton(R.string.add_url, (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                // Remove protocol if present
                url = url.replaceAll("^https?://", "");
                url = url.replaceAll("/$", ""); // Remove trailing slash

                if (urlList.contains(url)) {
                    Toast.makeText(this, R.string.url_exists, Toast.LENGTH_SHORT).show();
                } else {
                    urlList.add(url);
                    adapter.notifyDataSetChanged();
                    saveUrls();
                    Toast.makeText(this, R.string.url_added, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showRemoveUrlDialog(final String url, final int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_url)
                .setMessage(getString(R.string.remove_url) + ": " + url + "?")
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    urlList.remove(position);
                    adapter.notifyDataSetChanged();
                    saveUrls();
                    Toast.makeText(this, R.string.url_removed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showImportUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.import_url_list);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.enter_import_url);

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

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String importUrl = input.getText().toString().trim();
            if (!importUrl.isEmpty()) {
                if (!importUrl.startsWith("http://") && !importUrl.startsWith("https://")) {
                    importUrl = "https://" + importUrl;
                }
                new ImportUrlTask().execute(importUrl);
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private class ImportUrlTask extends AsyncTask<String, Void, List<String>> {
        private String errorMessage = null;

        @Override
        protected List<String> doInBackground(String... urls) {
            List<String> importedUrls = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urls[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    errorMessage = "HTTP error: " + responseCode;
                    return importedUrls;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        // Remove protocol if present
                        line = line.replaceAll("^https?://", "");
                        line = line.replaceAll("/$", "");
                        importedUrls.add(line);
                    }
                }
                reader.close();
            } catch (java.net.MalformedURLException e) {
                errorMessage = "Invalid URL format";
                e.printStackTrace();
            } catch (java.io.IOException e) {
                errorMessage = "Network error: " + e.getMessage();
                e.printStackTrace();
            } catch (Exception e) {
                errorMessage = "Error: " + e.getMessage();
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return importedUrls;
        }

        @Override
        protected void onPostExecute(List<String> importedUrls) {
            if (errorMessage != null) {
                Toast.makeText(URLManagementActivity.this,
                        getString(R.string.import_error) + ": " + errorMessage,
                        Toast.LENGTH_LONG).show();
            } else if (importedUrls != null && !importedUrls.isEmpty()) {
                int added = 0;
                for (String url : importedUrls) {
                    if (!urlList.contains(url)) {
                        urlList.add(url);
                        added++;
                    }
                }
                adapter.notifyDataSetChanged();
                saveUrls();
                Toast.makeText(URLManagementActivity.this,
                        getString(R.string.import_success) + " (" + added + " URLs)",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(URLManagementActivity.this, R.string.import_error, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
