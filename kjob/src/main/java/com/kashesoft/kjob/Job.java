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

import android.os.Process;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Job {

    //region Mode

    public enum Mode {
        AUTO, MANUAL
    }

    //endregion

    //region Data

    private JobState state = new EjectedState();
    private final ArrayDeque<Command> commands = new ArrayDeque<>();
    private long idCounter = 0;
    private Semaphore semaphore = new Semaphore(1);
    private boolean locked = false;

    //endregion

    //region Construction

    public Job(Mode mode) {
        switch (mode) {
            case AUTO:
                setState(new PausedState());
                break;
            case MANUAL:
                setState(new StoppedState());
                break;
        }
    }

    //endregion

    //region Destruction

    @Override
    protected void finalize() {
        setState(new EjectedState());
        try {
            super.finalize();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    //endregion

    //region String info

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    //endregion

    //region Logging control

    public static void setLogging(boolean logging) {
        Logger.setEnabled(logging);
    }

    //endregion

    //region Custom queues control

    public static void addQueue(String tag, int priority) {
        Shell.INSTANCE.addQueue(tag, priority);
    }

    public static void removeQueue(String tag) {
        Shell.INSTANCE.removeQueue(tag);
    }

    //endregion

    //MARK: - Public operations & utils

    public synchronized final Job resume() {
        state.resumeJob(this);
        return this;
    }

    public synchronized final Job suspend() {
        state.suspendJob(this);
        return this;
    }

    public synchronized final Job cancel() {
        state.cancelJob(this);
        return this;
    }

    public final void await() {
        if (!locked) {
            return;
        }
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }

    public final boolean await(double period) {
        if (!locked) {
            return true;
        }
        long periodTime = (long) (period * 1000);
        boolean waited = false;
        try {
            waited = semaphore.tryAcquire(periodTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (waited) {
                semaphore.release();
            }
        }
        return waited;
    }

    public synchronized final boolean hasCommands() {
        return !commands.isEmpty();
    }

    public synchronized final Exception error() {
        Command command = commands.peekFirst();
        if (command != null) {
            return command.error();
        }
        return null;
    }

    //endregion

    //region Private operations & utils

    private synchronized void dispatchCommand(Command command) {
        state.dispatchCommandToJob(command, this);
    }

    synchronized final void shiftCommand() {
        state.shiftCommandInJob(this);
    }

    synchronized final long nextCommandId() {
        if (idCounter == Long.MAX_VALUE) {
            idCounter = 0L;
        }
        return ++idCounter;
    }

    final void setState(JobState state) {
        if (state instanceof StartedState) {
            willStart();
        } else if (state instanceof PausedState) {
            willPause();
        } else if (state instanceof StoppedState) {
            willStop();
        } else if (state instanceof EjectedState) {
            willEject();
        }
        Logger.log("[" + this + "] state: " + this.state + " ==> " + state);
        this.state = state;
        if (state instanceof StartedState) {
            lockJob();
            Shell.INSTANCE.addJob(this);
            didStart();
        } else if (state instanceof FinishedState) {
            didFinish();
        } else if (state instanceof PausedState) {
            didPause();
            Shell.INSTANCE.removeJob(this);
            unlockJob();
        } else if (state instanceof InterruptedState) {
            didInterrupt();
        } else if (state instanceof StoppedState) {
            didStop();
            Shell.INSTANCE.removeJob(this);
            unlockJob();
        }
    }

    private void lockJob() {
        if (!locked) {
            try {
                semaphore.acquire();
                locked = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void unlockJob() {
        if (locked) {
            semaphore.release();
            locked = false;
        }
    }


    final void enqueueCommand(Command command) {
        commands.add(command);
        Logger.log("<" + command + "> is enqueued");
    }

    final boolean dequeueCommand() {
        Command command = commands.peekFirst();
        if (command != null) {
            switch (command.getStatus()) {
                case SUCCEEDED:
                case FAILED:
                case CANCELED:
                    commands.removeFirst();
                    Logger.log("<" + command + "> is dequeued");
                    return true;
            }
        }
        return false;
    }

    final boolean canLaunchCommand() {
        Command command = commands.peekFirst();
        if (command != null) {
            switch (command.getStatus()) {
                case READY:
                case FAILED:
                case SUSPENDED:
                    return true;
            }
        }
        return false;
    }

    final void launchCommand() {
        Command command = commands.peekFirst();
        if (command != null) {
            command.launch();
        }
    }

    final void suspendCommands() {
        for (Command command : commands) {
            switch (command.getStatus()) {
                case READY:
                case LAUNCHED:
                case FAILED:
                    command.suspend();
            }
        }
    }

    final void cancelCommands() {
        for (Command command : commands) {
            switch (command.getStatus()) {
                case READY:
                case LAUNCHED:
                case FAILED:
                case SUSPENDED:
                    command.cancel();
            }
        }
    }

    final boolean hasError() {
        Command command = commands.peekFirst();
        return command != null && (command.error() != null);
    }

    //endregion

    //region Lifecycle callbacks

    protected void willStart() {

    }

    protected void didStart() {

    }

    protected void didFinish() {

    }

    protected void willPause() {

    }

    protected void didPause() {

    }

    protected void didInterrupt() {

    }

    protected void willStop() {

    }

    protected void didStop() {

    }

    protected void willEject() {

    }

    //endregion

    //region Call commands

    public static Job doingInMain(Actable actable) {
        return new Job(Mode.AUTO).doInMain(actable);
    }

    public static Job doingInMain(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInMain(delay, actable);
    }

    public static Job doingInLowest(Actable actable) {
        return new Job(Mode.AUTO).doInLowest(actable);
    }

    public static Job doingInLowest(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInLowest(delay, actable);
    }

    public static Job doingInBackground(Actable actable) {
        return new Job(Mode.AUTO).doInBackground(actable);
    }

    public static Job doingInBackground(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInBackground(delay, actable);
    }

    public static Job doingInDefault(Actable actable) {
        return new Job(Mode.AUTO).doInDefault(actable);
    }

    public static Job doingInDefault(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInDefault(delay, actable);
    }

    public static Job doingInForeground(Actable actable) {
        return new Job(Mode.AUTO).doInForeground(actable);
    }

    public static Job doingInForeground(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInForeground(delay, actable);
    }

    public static Job doingInDisplay(Actable actable) {
        return new Job(Mode.AUTO).doInDisplay(actable);
    }

    public static Job doingInDisplay(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInDisplay(delay, actable);
    }

    public static Job doingInUrgentDisplay(Actable actable) {
        return new Job(Mode.AUTO).doInUrgentDisplay(actable);
    }

    public static Job doingInUrgentDisplay(double delay, Actable actable) {
        return new Job(Mode.AUTO).doInUrgentDisplay(delay, actable);
    }

    public static Job doingInCustom(String tag, Actable actable) {
        return new Job(Mode.AUTO).doInCustom(tag, actable);
    }

    public static Job doingInCustom(String tag, double delay, Actable actable) {
        return new Job(Mode.AUTO).doInCustom(tag, delay, actable);
    }

    public final Job doInMain(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithMainPriority(), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInMain(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithMainPriority(), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInLowest(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_LOWEST), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInLowest(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_LOWEST), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInBackground(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_BACKGROUND), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInBackground(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_BACKGROUND), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInDefault(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DEFAULT), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInDefault(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DEFAULT), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInForeground(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_FOREGROUND), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInForeground(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_FOREGROUND), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInDisplay(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DISPLAY), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInDisplay(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DISPLAY), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInUrgentDisplay(Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInUrgentDisplay(double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInCustom(String tag, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithTag(tag), null, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    public final Job doInCustom(String tag, double delay, Actable actable) {
        Command command = new Command(new WeakReference<>(this), Worker.workerQueueingWithTag(tag), delay, new Act(actable));
        dispatchCommand(command);
        return this;
    }

    //endregion

    //region Action commands

    public static boolean addTarget(Object target) {
        return Shell.INSTANCE.addTarget(target);
    }

    public static boolean removeTarget(Object target) {
        return Shell.INSTANCE.removeTarget(target);
    }

    public static <T> Job doingInMain(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInMain(actable);
    }

    public static <T> Job doingInMain(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInMain(delay, actable);
    }

    public static <T> Job doingInLowest(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInLowest(actable);
    }

    public static <T> Job doingInLowest(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInLowest(delay, actable);
    }

    public static <T> Job doingInBackground(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInBackground(actable);
    }

    public static <T> Job doingInBackground(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInBackground(delay, actable);
    }

    public static <T> Job doingInDefault(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInDefault(actable);
    }

    public static <T> Job doingInDefault(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInDefault(delay, actable);
    }

    public static <T> Job doingInForeground(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInForeground(actable);
    }

    public static <T> Job doingInForeground(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInForeground(delay, actable);
    }

    public static <T> Job doingInDisplay(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInDisplay(actable);
    }

    public static <T> Job doingInDisplay(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInDisplay(delay, actable);
    }

    public static <T> Job doingInUrgentDisplay(ActableO<T> actable) {
        return new Job(Mode.AUTO).doInUrgentDisplay(actable);
    }

    public static <T> Job doingInUrgentDisplay(double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInUrgentDisplay(delay, actable);
    }

    public static <T> Job doingInCustom(String tag, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInCustom(tag, actable);
    }

    public static <T> Job doingInCustom(String tag, double delay, ActableO<T> actable) {
        return new Job(Mode.AUTO).doInCustom(tag, delay, actable);
    }

    public final <T> Job doInMain(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithMainPriority(), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInMain(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithMainPriority(), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInLowest(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_LOWEST), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInLowest(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_LOWEST), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInBackground(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_BACKGROUND), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInBackground(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_BACKGROUND), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInDefault(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DEFAULT), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInDefault(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DEFAULT), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInForeground(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_FOREGROUND), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInForeground(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_FOREGROUND), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInDisplay(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DISPLAY), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInDisplay(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DISPLAY), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInUrgentDisplay(ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInUrgentDisplay(double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInCustom(String tag, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithTag(tag), null, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    public final <T> Job doInCustom(String tag, double delay, ActableO<T> actable) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithTag(tag), delay, new Action<>(actable));
        dispatchCommand(command);
        return this;
    }

    //endregion

    //region Reaction commands

    @SuppressWarnings("unchecked")
    public static <E> boolean addAction(Action<E> reaction) {
        return Shell.INSTANCE.addAction((Action<Object>) reaction);
    }

    @SuppressWarnings("unchecked")
    public static <E> boolean removeAction(Action<E> reaction) {
        return Shell.INSTANCE.removeAction((Action<Object>) reaction);
    }

    public static <E> Job postingInMain(E event) {
        return new Job(Mode.AUTO).postInMain(event);
    }

    public static <E> Job postingInMain(double delay, E event) {
        return new Job(Mode.AUTO).postInMain(delay, event);
    }

    public static <E> Job postingInLowest(E event) {
        return new Job(Mode.AUTO).postInLowest(event);
    }

    public static <E> Job postingInLowest(double delay, E event) {
        return new Job(Mode.AUTO).postInLowest(delay, event);
    }

    public static <E> Job postingInBackground(E event) {
        return new Job(Mode.AUTO).postInBackground(event);
    }

    public static <E> Job postingInBackground(double delay, E event) {
        return new Job(Mode.AUTO).postInBackground(delay, event);
    }

    public static <E> Job postingInDefault(E event) {
        return new Job(Mode.AUTO).postInDefault(event);
    }

    public static <E> Job postingInDefault(double delay, E event) {
        return new Job(Mode.AUTO).postInDefault(delay, event);
    }

    public static <E> Job postingInForeground(E event) {
        return new Job(Mode.AUTO).postInForeground(event);
    }

    public static <E> Job postingInForeground(double delay, E event) {
        return new Job(Mode.AUTO).postInForeground(delay, event);
    }

    public static <E> Job postingInDisplay(E event) {
        return new Job(Mode.AUTO).postInDisplay(event);
    }

    public static <E> Job postingInDisplay(double delay, E event) {
        return new Job(Mode.AUTO).postInDisplay(delay, event);
    }

    public static <E> Job postingInUrgentDisplay(E event) {
        return new Job(Mode.AUTO).postInUrgentDisplay(event);
    }

    public static <E> Job postingInUrgentDisplay(double delay, E event) {
        return new Job(Mode.AUTO).postInUrgentDisplay(delay, event);
    }

    public static <E> Job postingInCustom(String tag, E event) {
        return new Job(Mode.AUTO).postInCustom(tag, event);
    }

    public static <E> Job postingInCustom(String tag, double delay, E event) {
        return new Job(Mode.AUTO).postInCustom(tag, delay, event);
    }

    public final <E> Job postInMain(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithMainPriority(), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInMain(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithMainPriority(), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInLowest(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_LOWEST), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInLowest(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_LOWEST), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInBackground(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_BACKGROUND), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInBackground(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_BACKGROUND), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInDefault(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DEFAULT), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInDefault(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DEFAULT), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInForeground(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_FOREGROUND), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInForeground(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_FOREGROUND), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInDisplay(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DISPLAY), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInDisplay(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_DISPLAY), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInUrgentDisplay(E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInUrgentDisplay(double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY), delay, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInCustom(String tag, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithTag(tag), null, event);
        dispatchCommand(command);
        return this;
    }

    public final <E> Job postInCustom(String tag, double delay, E event) {
        Command command = new Command<>(new WeakReference<>(this), Worker.workerQueueingWithTag(tag), delay, event);
        dispatchCommand(command);
        return this;
    }

    //endregion

}
