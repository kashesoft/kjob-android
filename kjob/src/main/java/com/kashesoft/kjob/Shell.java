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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

enum Shell {

    INSTANCE;

    private final Map<String, ScheduledThreadPoolExecutor> queues = new HashMap<>();

    private final List<Job> jobs = new ArrayList<>();

    private final List<Object> targets = new ArrayList<>();

    private final List<Action<Object>> actions = new ArrayList<>();

    static ScheduledThreadPoolExecutor newQueue(final int priority) {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(priority);
                        r.run();
                    }
                });
            }
        };
        ScheduledThreadPoolExecutor queue = new ScheduledThreadPoolExecutor(1, threadFactory);
        queue.setRemoveOnCancelPolicy(true);
        return queue;
    }

    synchronized ScheduledThreadPoolExecutor getQueue(String tag) {
        return queues.get(tag);
    }

    synchronized void addQueue(String tag, int priority) {
        queues.put(tag, newQueue(priority));
    }

    synchronized void removeQueue(String tag) {
        queues.remove(tag);
    }

    synchronized boolean addJob(Job job) {
        if (jobs.contains(job)) {
            return false;
        } else {
            jobs.add(job);
            return true;
        }
    }

    synchronized boolean removeJob(Job job) {
        return jobs.remove(job);
    }

    synchronized List<Object> getTargets() {
        return new ArrayList<>(targets);
    }

    synchronized boolean addTarget(Object target) {
        if (targets.contains(target)) {
            return false;
        } else {
            targets.add(target);
            return true;
        }
    }

    synchronized boolean removeTarget(Object target) {
        return targets.remove(target);
    }

    synchronized List<Action<Object>> getActions() {
        return new ArrayList<>(actions);
    }

    synchronized boolean addAction(Action<Object> action) {
        if (actions.contains(action)) {
            return false;
        } else {
            actions.add(action);
            return true;
        }
    }

    synchronized boolean removeAction(Action<Object> action) {
        return actions.remove(action);
    }

}
