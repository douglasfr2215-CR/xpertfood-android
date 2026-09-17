package br.com.xpertfood;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;

public class NotificationCaptureService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification item) {
        if(item.getPackageName().equals(getPackageName()))return;
        Notification n=item.getNotification();
        String raw=String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE,""))+" • "+String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT,""))+" • "+String.valueOf(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT,""));
        String low=(item.getPackageName()+" "+raw).toLowerCase(Locale.ROOT);
        if(low.contains("ifood")||(low.contains("r$")&&low.contains("km")))
            LiveOfferAnalyzer.analyzeAndShow(this,raw,"notificação:"+item.getPackageName());
    }
}
