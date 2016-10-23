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

abstract class JobState {
    abstract void dispatchCommandToJob(Command command, Job job);
    abstract void shiftCommandInJob(Job job);
    abstract void resumeJob(Job job);
    abstract void suspendJob(Job job);
    abstract void cancelJob(Job job);
    @Override
    public String toString() {
        return getClass().getSimpleName().replace("State", "").toUpperCase();
    }
}

class StartedState extends JobState {

    @Override
    void dispatchCommandToJob(Command command, Job job) {
        job.enqueueCommand(command);
    }

    @Override
    void shiftCommandInJob(Job job) {
        if (job.hasError()) {
            job.setState(new StoppedState());
        } else {
            job.setState(new FinishedState());
            while (job.dequeueCommand()) {}
            if (job.canLaunchCommand()) {
                job.setState(new StartedState());
                job.launchCommand();
            } else {
                job.setState(new PausedState());
            }
        }
    }

    @Override
    void resumeJob(Job job) {

    }

    @Override
    void suspendJob(Job job) {
        job.suspendCommands();
        job.setState(new InterruptedState());
    }

    @Override
    void cancelJob(Job job) {
        job.cancelCommands();
        while (job.dequeueCommand()) {}
    }

}

class FinishedState extends JobState {

    @Override
    void dispatchCommandToJob(Command command, Job job) {
        job.enqueueCommand(command);
    }

    @Override
    void shiftCommandInJob(Job job) {

    }

    @Override
    void resumeJob(Job job) {

    }

    @Override
    void suspendJob(Job job) {

    }

    @Override
    void cancelJob(Job job) {
        job.cancelCommands();
    }

}

class PausedState extends JobState {

    @Override
    void dispatchCommandToJob(Command command, Job job) {
        job.enqueueCommand(command);
        if (job.canLaunchCommand()) {
            job.setState(new StartedState());
            job.launchCommand();
        }
    }

    @Override
    void shiftCommandInJob(Job job) {

    }

    @Override
    void resumeJob(Job job) {

    }

    @Override
    void suspendJob(Job job) {
        job.setState(new StoppedState());
    }

    @Override
    void cancelJob(Job job) {

    }

}

class InterruptedState extends JobState {

    @Override
    void dispatchCommandToJob(Command command, Job job) {
        job.enqueueCommand(command);
    }

    @Override
    void shiftCommandInJob(Job job) {
        if (!job.hasError()) {
            while (job.dequeueCommand()) {}
        }
        job.setState(new StoppedState());
    }

    @Override
    void resumeJob(Job job) {

    }

    @Override
    void suspendJob(Job job) {

    }

    @Override
    void cancelJob(Job job) {

    }

}

class StoppedState extends JobState {

    @Override
    void dispatchCommandToJob(Command command, Job job) {
        job.enqueueCommand(command);
    }

    @Override
    void shiftCommandInJob(Job job) {

    }

    @Override
    void resumeJob(Job job) {
        if (job.canLaunchCommand()) {
            job.setState(new StartedState());
            job.launchCommand();
        } else {
            job.setState(new PausedState());
        }
    }

    @Override
    void suspendJob(Job job) {

    }

    @Override
    void cancelJob(Job job) {
        job.cancelCommands();
        while (job.dequeueCommand()) {}
    }

}

class EjectedState extends JobState {

    @Override
    void dispatchCommandToJob(Command command, Job job) {

    }

    @Override
    void shiftCommandInJob(Job job) {

    }

    @Override
    void resumeJob(Job job) {

    }

    @Override
    void suspendJob(Job job) {

    }

    @Override
    void cancelJob(Job job) {

    }

}
