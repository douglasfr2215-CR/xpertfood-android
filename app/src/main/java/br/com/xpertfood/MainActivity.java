package br.com.xpertfood;

import android.app.Activity;
import android.content.Intent;
import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private WebView web;
    private String pendingCsv;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != getPackageManager().PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 98);
        web = new WebView(this); setContentView(web);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android"); web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onResume() {
        super.onResume();
        if (web == null) return;
        String pending = getSharedPreferences("xpertfood_live", MODE_PRIVATE).getString("pending_offers", "[]");
        getSharedPreferences("xpertfood_live", MODE_PRIVATE).edit().putString("pending_offers", "[]").apply();
        try {
            JSONArray offers = new JSONArray(pending);
            for (int i = 0; i < offers.length(); i++) {
                final String json = offers.getJSONObject(i).toString();
                web.postDelayed(() -> web.evaluateJavascript("window.receiveLiveOffer&&window.receiveLiveOffer(" + json + ")", null), 500 + (i * 80));
            }
        } catch (Exception ignored) {}
    }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 99 && resultCode == RESULT_OK && data != null && pendingCsv != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                web.evaluateJavascript("alert('CSV salvo com sucesso.')", null);
            } catch (Exception e) { web.evaluateJavascript("alert('Não foi possível salvar o CSV.')", null); }
            pendingCsv = null;
        }
    }
    public class Bridge {
        @JavascriptInterface public void openNotificationAccess() { runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); }
        @JavascriptInterface public void openAccessibility() { runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); }
        @JavascriptInterface public void openOverlayAccess() { runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }); }
        @JavascriptInterface public void testLiveMonitor() { LiveOfferAnalyzer.analyzeAndShow(MainActivity.this, "iFood • Restaurante: Lanchonete Central • Endereço: Rua Adolfo Olinto, 120 • R$ 7,50 • 1,0 km até coleta • 4,0 km entrega • 20 min", "teste"); }
        @JavascriptInterface public void shareCsv(String csv) { runOnUiThread(() -> {
            try {
                pendingCsv = csv;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("text/csv"); i.putExtra(Intent.EXTRA_TITLE, "xpertfood_entregas.csv");
                startActivityForResult(i, 99);
            } catch (Exception e) { web.evaluateJavascript("alert('Não foi possível exportar o CSV.')", null); }
        }); }
    }
}
