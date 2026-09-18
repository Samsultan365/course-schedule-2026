package com.samsultan365.courseschedule;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.Manifest;
import android.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class ReminderManager {
    private static final String SCHEDULE_URL = "https://samsultan365.github.io/course-schedule-2026/schedule.json";
    private static final String PREFS = "course_reminders";
    private static final String KEY_IDS = "scheduled_ids";
    private static final String KEY_ENABLED = "reminders_enabled";
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    public static final String ACTION_SHOW = "com.samsultan365.courseschedule.action.SHOW_REMINDER";
    public static final String ACTION_SNOOZE = "com.samsultan365.courseschedule.action.SNOOZE_REMINDER";
    public static final String EXTRA_ID = "event_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_DATE = "date";

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            sync(context);
        } else {
            cancelAll(context);
        }
    }

    private static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> oldIds = new HashSet<>(prefs.getStringSet(KEY_IDS, new HashSet<>()));
        for (String oldId : oldIds) {
            cancel(context, alarmManager, Integer.parseInt(oldId));
        }
        prefs.edit().putStringSet(KEY_IDS, new HashSet<>()).apply();
    }

    public static void sync(Context context) {
        if (!isEnabled(context)) {
            cancelAll(context);
            return;
        }        new Thread(() -> {
            try {
                String json = httpGet(SCHEDULE_URL);
                JSONObject root = new JSONObject(json);
                JSONArray events = root.getJSONArray("events");
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                Set<String> oldIds = new HashSet<>(prefs.getStringSet(KEY_IDS, new HashSet<>()));
                Set<String> newIds = new HashSet<>();
                long now = System.currentTimeMillis();
                long horizon = now + 30L * DAY_MILLIS;
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

                for (int i = 0; i < events.length(); i++) {
                    JSONObject event = events.getJSONObject(i);
                    String date = event.getString("date");
                    String start = event.getString("start");
                    String end = event.optString("end", "");
                    String title = event.getString("title");
                    String location = event.optString("location", "");
                    int reminderMinutes = event.optInt("reminder_minutes", 30);
                    String status = event.optString("status", "confirmed");
                    Date startDate = format.parse(date + " " + start);
                    long startMillis = startDate.getTime();
                    long triggerMillis = startMillis - reminderMinutes * 60L * 1000L;
                    if (triggerMillis <= now || triggerMillis > horizon) {
                        continue;
                    }
                    int id = stableId(date, start, title);
                    newIds.add(String.valueOf(id));
                    schedule(context, alarmManager, triggerMillis, id, date, start + "–" + end, title, location, status);
                }

                for (String oldId : oldIds) {
                    if (!newIds.contains(oldId)) {
                        cancel(context, alarmManager, Integer.parseInt(oldId));
                    }
                }
                prefs.edit().putStringSet(KEY_IDS, newIds).apply();
            } catch (Exception ignored) {
            }
        }).start();
    }

    private static void schedule(Context context, AlarmManager alarmManager, long triggerMillis, int id,
                                 String date, String time, String title, String location, String status) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_SHOW);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_DATE, date);
        intent.putExtra(EXTRA_TIME, time);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_LOCATION, location);
        intent.putExtra(EXTRA_STATUS, status);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean exact = canScheduleExact(alarmManager);
        if (exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
        }
    }

    private static void cancel(Context context, AlarmManager alarmManager, int id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_SHOW);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    private static boolean canScheduleExact(AlarmManager alarmManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    public static void ensurePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(activity)
                    .setTitle("开启课程提醒")
                    .setMessage("需要通知权限，才能在上课前和晚自习前提醒你。")
                    .setPositiveButton("开启", (d, w) -> activity.requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001))
                    .setNegativeButton("稍后", null)
                    .show();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(activity)
                        .setTitle("开启精确提醒")
                        .setMessage("为了锁屏时也能准时提醒，请允许精确闹钟。")
                        .setPositiveButton("去开启", (d, w) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("稍后", null)
                        .show();
            }
        }
    }

    private static int stableId(String date, String start, String title) {
        return Math.abs((date + "#" + start + "#" + title).hashCode());
    }

    private static String httpGet(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
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
}