package com.samsultan365.courseschedule;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "course_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ReminderManager.ACTION_SHOW.equals(action)) {
            showReminder(context, intent);
        } else if (ReminderManager.ACTION_SNOOZE.equals(action)) {
            snooze(context, intent);
        }
    }

    private void showReminder(Context context, Intent intent) {
        int id = intent.getIntExtra(ReminderManager.EXTRA_ID, 0);
        String date = intent.getStringExtra(ReminderManager.EXTRA_DATE);
        String time = intent.getStringExtra(ReminderManager.EXTRA_TIME);
        String title = intent.getStringExtra(ReminderManager.EXTRA_TITLE);
        String location = intent.getStringExtra(ReminderManager.EXTRA_LOCATION);
        String status = intent.getStringExtra(ReminderManager.EXTRA_STATUS);

        createChannel(context);
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
        snoozeIntent.setAction(ReminderManager.ACTION_SNOOZE);
        snoozeIntent.putExtra(ReminderManager.EXTRA_ID, id);
        snoozeIntent.putExtra(ReminderManager.EXTRA_DATE, date);
        snoozeIntent.putExtra(ReminderManager.EXTRA_TIME, time);
        snoozeIntent.putExtra(ReminderManager.EXTRA_TITLE, title);
        snoozeIntent.putExtra(ReminderManager.EXTRA_LOCATION, location);
        snoozeIntent.putExtra(ReminderManager.EXTRA_STATUS, status);
        PendingIntent snoozePending = PendingIntent.getBroadcast(context, id + 1, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = "时间：" + time;
        if (location != null && !location.isEmpty()) {
            text += "　地点：" + location;
        }
        if ("tentative".equals(status)) {
            title = title + "（待定）";
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .addAction(0, "稍后10分钟", snoozePending)
                .setGroup(date)
                .setSortKey(date + "-" + time);

        Notification.Builder publicBuilder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification publicNotification = publicBuilder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("有课程提醒")
                .build();
        builder.setPublicVersion(publicNotification);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(id, builder.build());
    }

    private void snooze(Context context, Intent intent) {
        int id = intent.getIntExtra(ReminderManager.EXTRA_ID, 0);
        String date = intent.getStringExtra(ReminderManager.EXTRA_DATE);
        String time = intent.getStringExtra(ReminderManager.EXTRA_TIME);
        String title = intent.getStringExtra(ReminderManager.EXTRA_TITLE);
        String location = intent.getStringExtra(ReminderManager.EXTRA_LOCATION);
        String status = intent.getStringExtra(ReminderManager.EXTRA_STATUS);

        Intent next = new Intent(context, ReminderReceiver.class);
        next.setAction(ReminderManager.ACTION_SHOW);
        next.putExtra(ReminderManager.EXTRA_ID, id);
        next.putExtra(ReminderManager.EXTRA_DATE, date);
        next.putExtra(ReminderManager.EXTRA_TIME, time);
        next.putExtra(ReminderManager.EXTRA_TITLE, title);
        next.putExtra(ReminderManager.EXTRA_LOCATION, location);
        next.putExtra(ReminderManager.EXTRA_STATUS, status);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, next,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long trigger = System.currentTimeMillis() + 10L * 60L * 1000L;
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "课程提醒",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("课程和晚自习提醒");
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}