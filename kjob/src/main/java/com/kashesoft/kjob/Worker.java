/*
 * Copyright (C) 2016 Andrey Kashaed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kashesoft.kjob;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class Worker {

    static Worker workerQueueingWithMainPriority() {
        return new Worker(null, null);
    }

    static Worker workerQueueingWithPriority(Integer priority) {
        return new Worker(priority, null);
    }

    static Worker workerQueueingWithTag(String tag) {
        return new Worker(null, tag);
    }

    private final Integer priority;
    private final String tag;

    private Worker(Integer priority, String tag) {
        this.priority = priority;
        this.tag = tag;
    }

    void workUpTask(final FutureTask<Void> task, Double delay) {
        final ScheduledThreadPoolExecutor queue;
        if (tag != null) {
            queue = Shell.INSTANCE.getQueue(tag);
        } else if (priority != null) {
            queue = Shell.newQueue(priority);
        } else {
            queue = null;
        }
        if (delay != null) {
            long delayTime = (long) (delay * 1000);
            if (queue != null) {
                queue.schedule(task, delayTime, TimeUnit.MILLISECONDS);
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(task, delayTime);
            }
        } else {
            if (queue != null) {
                queue.execute(task);
            } else {
                new Handler(Looper.getMainLooper()).post(task);
            }
        }
    }

}
