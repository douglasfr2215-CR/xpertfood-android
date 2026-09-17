package br.com.xpertfood;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiveOfferAnalyzer {
    private static final Pattern MONEY=Pattern.compile("R\\$\\s*(\\d+(?:[.,]\\d{1,2})?)",Pattern.CASE_INSENSITIVE);
    private static final Pattern KM=Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*km",Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN=Pattern.compile("(\\d+)\\s*min",Pattern.CASE_INSENSITIVE);
    private static final Pattern SECONDS=Pattern.compile("(\\d{1,2})\\s*(?:s|seg|segundos?)\\b",Pattern.CASE_INSENSITIVE);
    private static WindowManager currentWindowManager;
    private static View currentCard;
    private LiveOfferAnalyzer(){}
    public static void analyzeAndShow(Context c,String raw,String source){
        raw=raw.replaceAll("\\s+"," ").trim(); if(raw.isEmpty())return;
        SharedPreferences p=c.getSharedPreferences("xpertfood_live",Context.MODE_PRIVATE); long now=System.currentTimeMillis(); int sig=raw.toLowerCase(Locale.ROOT).hashCode();
        if(p.getInt("last_sig",0)==sig&&now-p.getLong("last_at",0)<45000)return;
        p.edit().putInt("last_sig",sig).putLong("last_at",now).apply();
        double fare=first(MONEY,raw); ArrayList<Double> kms=all(KM,raw); double pickup=kms.size()>0?kms.get(0):0,trip=kms.size()>1?kms.get(1):0,total=pickup+trip; int mins=(int)first(MIN,raw);
        double rpkm=total>0?fare/total:0,rph=mins>0?fare*60/mins:0; String verdict=rpkm>=1.10&&rph>=22.50?"aceitar":rpkm>=.90?"avaliar":"recusar";
        if(fare<=0||total<=0)return;
        String label=verdict.toUpperCase(Locale.ROOT); String detail=String.format(Locale.forLanguageTag("pt-BR"),"R$ %.2f/km • %.1f km efetivos%s",rpkm,total,mins>0?String.format(Locale.forLanguageTag("pt-BR")," • R$ %.2f/h",rph):"");
        try{JSONObject o=new JSONObject().put("signature",sig).put("source",source).put("fare",fare).put("pickupKm",pickup).put("tripKm",trip).put("totalKm",total).put("mins",mins).put("rpkm",rpkm).put("rph",rph).put("verdict",verdict);queue(p,o);}catch(Exception ignored){}
        int seconds=(int)first(SECONDS,raw); long popupMs=(seconds>=5&&seconds<=90?seconds:90)*1000L;
        notify(c,label,detail,verdict); overlay(c,label,detail,verdict,popupMs);
    }
    private static double first(Pattern pattern,String text){Matcher m=pattern.matcher(text);if(!m.find())return 0;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return 0;}}
    private static synchronized void queue(SharedPreferences p,JSONObject offer){try{JSONArray a=new JSONArray(p.getString("pending_offers","[]"));while(a.length()>=100)a.remove(0);a.put(offer);p.edit().putString("pending_offers",a.toString()).apply();}catch(Exception ignored){}}
    private static ArrayList<Double> all(Pattern pattern,String text){ArrayList<Double> values=new ArrayList<>();Matcher m=pattern.matcher(text);while(m.find()&&values.size()<3)try{values.add(Double.parseDouble(m.group(1).replace(',','.')));}catch(Exception ignored){}return values;}
    private static int color(String v){return v.equals("aceitar")?0xff158f58:v.equals("avaliar")?0xffbd8200:0xffd93645;}
    private static void notify(Context c,String label,String detail,String verdict){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);String ch="monitor_entregas";if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"Monitor de entregas",NotificationManager.IMPORTANCE_HIGH));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,ch):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(label+" — oferta iFood").setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setColor(color(verdict)).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH);try{nm.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),b.build());}catch(Exception ignored){}}
    public static void dismissOverlay(){new Handler(Looper.getMainLooper()).post(()->{try{if(currentWindowManager!=null&&currentCard!=null)currentWindowManager.removeView(currentCard);}catch(Exception ignored){}finally{currentWindowManager=null;currentCard=null;}});}
    private static void overlay(Context c,String label,String detail,String verdict,long popupMs){
        OfferCaptureAccessibilityService service=OfferCaptureAccessibilityService.getActiveService();
        final Context overlayContext=service!=null?service:c;
        final boolean accessibilityOverlay=service!=null;
        if(!accessibilityOverlay&&Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(c))return;
        new Handler(Looper.getMainLooper()).post(()->{dismissOverlay();WindowManager wm=(WindowManager)overlayContext.getSystemService(Context.WINDOW_SERVICE);TextView card=new TextView(overlayContext);card.setText(label+" · "+detail);card.setTextColor(Color.WHITE);card.setTextSize(14);card.setGravity(Gravity.CENTER);card.setPadding(14,8,14,8);card.setBackgroundColor(color(verdict));int type=accessibilityOverlay?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:(Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE);WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=80;try{wm.addView(card,lp);currentWindowManager=wm;currentCard=card;card.setOnClickListener(v->dismissOverlay());new Handler(Looper.getMainLooper()).postDelayed(LiveOfferAnalyzer::dismissOverlay,popupMs);}catch(Exception ignored){}});
    }
}
