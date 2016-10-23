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

import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

class Command<O> {

    enum Status {
        READY, LAUNCHED, EXECUTING, SUCCEEDED, FAILED, SUSPENDED, CANCELED
    }

    private final long id;
    private final WeakReference<Job> job;
    private final Worker worker;
    private final Double delay;
    private final Callable<Void> block;
    private FutureTask<Void> task;
    private Status status = Status.READY;
    private Exception error;

    Command(final WeakReference<Job> job, Worker worker, Double delay, final Act act) {
        this(
                job,
                worker,
                delay,
                new java.util.concurrent.Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        if (job.get() != null) {
                            act.act();
                        }
                        return null;
                    }
                }
        );
    }

    Command(final WeakReference<Job> job, Worker worker, Double delay, final Action<O> action) {
        this(
                job,
                worker,
                delay,
                new java.util.concurrent.Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        if (job.get() != null) {
                            for (Object target : Shell.INSTANCE.getTargets()) {
                                action.act(target);
                            }
                        }
                        return null;
                    }
                }
        );
    }

    Command(final WeakReference<Job> job, Worker worker, Double delay, final O event) {
        this(
                job,
                worker,
                delay,
                new java.util.concurrent.Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        if (job.get() != null) {
                            for (Action action : Shell.INSTANCE.getActions()) {
                                action.act(event);
                            }
                        }
                        return null;
                    }
                }
        );
    }

    private Command(final WeakReference<Job> job, Worker worker, Double delay, final Callable<Void> block) {
        this.id = job.get().nextCommandId();
        this.job = job;
        this.worker = worker;
        this.delay = delay;
        final WeakReference<Command<O>> weakThis = new WeakReference<>(this);
        this.block = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Command strongThis = weakThis.get();
                if (strongThis == null) {
                    return null;
                }
                synchronized (strongThis) {
                    if (strongThis.status != Status.LAUNCHED) {
                        return null;
                    }
                    strongThis.error = null;
                    strongThis.setStatus(Status.EXECUTING);
                    try {
                        block.call();
                        strongThis.setStatus(Status.SUCCEEDED);
                    } catch (Exception e) {
                        strongThis.error = e;
                        strongThis.setStatus(Status.FAILED);
                    }
                }
                return null;
            }
        };
    }

    @Override
    public String toString() {
        return job.get() + "." + getClass().getSimpleName() + ":" + id;
    }

    synchronized Status getStatus() {
        return status;
    }

    Exception error() {
        return error;
    }

    synchronized void launch() {
        setStatus(Status.LAUNCHED);
        setUpTask();
        worker.workUpTask(task, delay);
    }

    synchronized void suspend() {
        setStatus(Status.SUSPENDED);
    }

    synchronized void cancel() {
        setStatus(Status.CANCELED);
        if (task != null) {
            task.cancel(true);
        }
    }

    private void setStatus(Status status) {
        Logger.log("<" + this + "> status: " + this.status.name().toUpperCase() + " --> " + status.name().toUpperCase());
        this.status = status;
    }

    private void setUpTask() {
        task = new FutureTask<Void>(block) {
            @Override
            protected void done() {
                Job strongJob = job.get();
                if (strongJob != null) {
                    strongJob.shiftCommand();
                }
            }
        };
    }

}
