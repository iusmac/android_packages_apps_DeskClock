/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.data;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.android.deskclock.NotificationUtils.STOPWATCH_NOTIFICATION_CHANNEL_ID;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.SystemClock;
import android.widget.RemoteViews;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.content.ContextCompat;

import com.android.deskclock.NotificationUtils;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.events.Events;
import com.android.deskclock.stopwatch.StopwatchService;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds notification to reflect the latest state of the stopwatch and recorded laps.
 */
class StopwatchNotificationBuilder {

    public Notification build(Context context, NotificationModel nm, Stopwatch stopwatch) {
        @StringRes final int eventLabel = R.string.label_notification;

        // Intent to load the app when the notification is tapped.
        final Intent showApp = new Intent(context, StopwatchService.class)
                .setAction(StopwatchService.ACTION_SHOW_STOPWATCH)
                .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

        final PendingIntent pendingShowApp = PendingIntent.getService(context, 0, showApp,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE);

        // Compute some values required below.
        final boolean running = stopwatch.isRunning();
        final String pname = context.getPackageName();
        final Resources res = context.getResources();
        final long base = SystemClock.elapsedRealtime() - stopwatch.getTotalTime();

        final RemoteViews content = new RemoteViews(pname, R.layout.chronometer_notif_content);
        content.setChronometer(R.id.chronometer, base, null, running);

        final List<Action> actions = new ArrayList<>(2);

        if (running) {
            // Left button: Pause
            final Intent pause = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_PAUSE_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            final CharSequence title1 = res.getText(R.string.sw_pause_button);
            final PendingIntent intent1 = Utils.pendingServiceIntent(context, pause);
            actions.add(new Action.Builder(null, title1, intent1).build());

            // Right button: Add Lap
            if (DataModel.getDataModel().canAddMoreLaps()) {
                final Intent lap = new Intent(context, StopwatchService.class)
                        .setAction(StopwatchService.ACTION_LAP_STOPWATCH)
                        .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

                final CharSequence title2 = res.getText(R.string.sw_lap_button);
                final PendingIntent intent2 = Utils.pendingServiceIntent(context, lap);
                actions.add(new Action.Builder(null, title2, intent2).build());
            }

            // Show the current lap number if any laps have been recorded.
            final int lapCount = DataModel.getDataModel().getLaps().size();
            if (lapCount > 0) {
                final int lapNumber = lapCount + 1;
                final String lap = res.getString(R.string.sw_notification_lap_number, lapNumber);
                content.setTextViewText(R.id.state, lap);
                content.setViewVisibility(R.id.state, VISIBLE);
            } else {
                content.setViewVisibility(R.id.state, GONE);
            }
        } else {
            // Left button: Start
            final Intent start = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_START_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            final CharSequence title1 = res.getText(R.string.sw_start_button);
            final PendingIntent intent1 = Utils.pendingServiceIntent(context, start);
            actions.add(new Action.Builder(null, title1, intent1).build());

            // Right button: Reset (dismisses notification and resets stopwatch)
            final Intent reset = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_RESET_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            final CharSequence title2 = res.getText(R.string.sw_reset_button);
            final PendingIntent intent2 = Utils.pendingServiceIntent(context, reset);
            actions.add(new Action.Builder(null, title2, intent2).build());

            // Indicate the stopwatch is paused.
            content.setTextViewText(R.id.state, res.getString(R.string.swn_paused));
            content.setViewVisibility(R.id.state, VISIBLE);
        }

        final Builder notification = new NotificationCompat.Builder(
                context, STOPWATCH_NOTIFICATION_CHANNEL_ID)
                        .setLocalOnly(true)
                        .setOngoing(running)
                        .setCustomContentView(content)
                        .setContentIntent(pendingShowApp)
                        .setAutoCancel(stopwatch.isPaused())
                        .setPriority(Notification.PRIORITY_LOW)
                        .setSmallIcon(R.drawable.stat_notify_stopwatch)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setColor(ContextCompat.getColor(context, R.color.default_background))
                        .setGroup(nm.getStopwatchNotificationGroupKey());

        for (Action action : actions) {
            notification.addAction(action);
        }

        NotificationUtils.createChannel(context, STOPWATCH_NOTIFICATION_CHANNEL_ID);
        return notification.build();
    }
}
