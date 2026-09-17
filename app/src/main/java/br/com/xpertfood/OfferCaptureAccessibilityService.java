package br.com.xpertfood;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class OfferCaptureAccessibilityService extends AccessibilityService {
    private static OfferCaptureAccessibilityService activeService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String activePackage = "";
    private final Runnable capture = () -> captureOffer(activePackage);
    private final Runnable finishOffer = LiveOfferAnalyzer::endActiveOffer;
    public static OfferCaptureAccessibilityService getActiveService() { return activeService; }
    @Override protected void onServiceConnected() { super.onServiceConnected(); activeService = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        activePackage = event.getPackageName().toString();
        if (activePackage.equals(getPackageName())) return;
        handler.removeCallbacks(capture); handler.postDelayed(capture, 280);
    }
    private void captureOffer(String sourcePackage) {
        AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) return;
        StringBuilder text = new StringBuilder(); collect(root, text, 0); root.recycle();
        String raw = text.toString().replaceAll("\\s+", " ").trim();
        String low = raw.toLowerCase(Locale.ROOT);
        boolean money = low.contains("r$") || low.matches(".*\\b\\d+[,.]\\d{2}\\b.*");
        boolean distance = low.contains(" km") || low.contains("quilômetro");
        boolean ride = low.contains("coleta") || low.contains("entrega") || low.contains("pedido") || low.contains("aceitar") || low.contains("ifood");
        if (money && distance && ride) {
            handler.removeCallbacks(finishOffer);
            LiveOfferAnalyzer.analyzeAndShow(this, raw, "tela:" + sourcePackage);
        } else {
            handler.removeCallbacks(finishOffer);
            handler.postDelayed(finishOffer, 1500);
        }
    }
    private void collect(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 30 || out.length() > 7000) return;
        CharSequence t=n.getText(), d=n.getContentDescription();
        if(t!=null&&t.length()>0)out.append(t).append(" • ");
        if(d!=null&&d.length()>0&&!d.equals(t))out.append(d).append(" • ");
        for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out,depth+1);
    }
    @Override public void onInterrupt(){handler.removeCallbacks(capture);handler.removeCallbacks(finishOffer);LiveOfferAnalyzer.endActiveOffer();}
    @Override public void onDestroy(){if(activeService==this)activeService=null;handler.removeCallbacks(capture);handler.removeCallbacks(finishOffer);LiveOfferAnalyzer.endActiveOffer();super.onDestroy();}
}
