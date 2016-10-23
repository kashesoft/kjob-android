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
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class JobTest {

    @Before
    public void setUp() throws Exception {
        Job.setLogging(true);
    }

    @After
    public void tearDown() throws Exception {
        System.gc();
        Job.setLogging(false);
    }

    @Test
    public void testJobsWithCustomQueue() {
        Job1 job1 = new Job1();
        Job2 job2 = new Job2();
        // Add custom queue.
        Job.addQueue(Demo.customQueueTag, Process.THREAD_PRIORITY_BACKGROUND);
        // Peform some commands.
        // Pay attention to that this commands will be executed serially.
        job1.doInCustom(Demo.customQueueTag, Demo.action1);
        job2.doInCustom(Demo.customQueueTag, Demo.action2);
        job1.doInCustom(Demo.customQueueTag, Demo.action3);
        job2.doInCustom(Demo.customQueueTag, Demo.action4);
        job1.await();
        job2.await();
        // Remove custom queue.
        Job.removeQueue(Demo.customQueueTag);
    }

    @Test
    public void testJobAwaitHandling() {
        MyJob job = new MyJob();
        // Peform a couple of commands and await during 5 seconds.
        job.doInBackground(Demo.action1);
        job.doInBackground(Demo.action2);
        boolean waitedFirstTime = job.await(5.0);
        Logger.log("waitedFirstTime = " + waitedFirstTime);
        // Peform a couple of commands and await during 5 seconds.
        // Pay attention to that we will not be waited for the last command.
        job.doInBackground(Demo.action3);
        job.doInBackground(Demo.action4);
        boolean waitedSecondTime = job.await(5.0);
        Logger.log("waitedSecondTime = " + waitedSecondTime);
        job.await();
    }

    @Test
    public void testJobErrorHandling() {
        MyJob job = new MyJob();
        // Perform a triple of commands with one-off error in the second one.
        // Pay attention to that third command will not be executed for the first time but will be executed for the second time.
        job.doInBackground(Demo.action1);
        job.doInBackground(Demo.action2WithError);
        job.doInBackground(Demo.action3);
        job.await();
        // Resume job and remaining commands will be executed.
        job.resume();
        job.await();
    }

    @Test
    public void testJobSuspendHandling() {
        MyJob job = new MyJob();
        // Perform a triple of commands and suspend job.
        // Pay attention to that only first command will be executed because it began execution before suspending.
        job.doInBackground(Demo.action1);
        job.doInBackground(Demo.action2);
        job.doInBackground(Demo.action3);
        job.suspend();
        job.await();
        // Resume job and remaining commands will be executed.
        job.resume();
        job.await();
    }

    @Test
    public void testJobCancelHandling() {
        MyJob job = new MyJob();
        // Perform a triple of commands and cancel job.
        // Pay attention to that only first command will be executed because it began execution before canceling.
        job.doInBackground(Demo.action1);
        job.doInBackground(Demo.action2);
        job.doInBackground(Demo.action3);
        job.cancel();
        // Add new command and it will be executed after the first one.
        job.doInBackground(Demo.action4);
        job.await();
    }

    @Test
    public void testTargetActionCommands() {
        MyJob job = new MyJob();
        // Add Target A.
        Job.addTarget(TargetA.INSTANCE);
        // Perform commands for Target A and Target B.
        // Pay attention to that only commands for Target A will be executed.
        job.doInBackground(1.0, Demo.action1ForTargetA);
        job.doInBackground(Demo.action2ForTargetA);
        job.doInBackground(1.0, Demo.action1ForTargetB);
        job.doInBackground(Demo.action2ForTargetB);
        job.await();
        // Remove Target A and add Target B.
        Job.removeTarget(TargetA.INSTANCE);
        Job.addTarget(TargetB.INSTANCE);
        // Perform commands for Target A and Target B.
        // Pay attention to that only commands for Target B will be executed.
        job.doInBackground(1.0, Demo.action1ForTargetA);
        job.doInBackground(Demo.action2ForTargetA);
        job.doInBackground(1.0, Demo.action1ForTargetB);
        job.doInBackground(Demo.action2ForTargetB);
        job.await();
    }

    @Test
    public void testEventActionCommands() {
        MyJob job = new MyJob();
        // Add actions for Event A.
        Job.addAction(Demo.action1ForEventA);
        Job.addAction(Demo.action2ForEventA);
        // Perform commands for Event A and Event B.
        // Pay attention to that only commands for Event A will be executed.
        job.postInBackground(1.0, new EventA());
        job.postInBackground(new EventB());
        job.await();
        // Remove actions for Event A and add actions for Event B.
        Job.removeAction(Demo.action1ForEventA);
        Job.removeAction(Demo.action2ForEventA);
        Job.addAction(Demo.action1ForEventB);
        Job.addAction(Demo.action2ForEventB);
        // Perform commands for Event A and Event B.
        // Pay attention to that only commands for Event B will be executed.
        job.postInBackground(1.0, new EventA());
        job.postInBackground(new EventB());
        job.await();
    }

}

class Job1 extends Job {

    Job1() {
        super(Mode.AUTO);
    }

}

class Job2 extends Job {

    Job2() {
        super(Mode.AUTO);
    }

}

class MyJob extends Job {

    MyJob() {
        super(Mode.AUTO);
    }

    @Override
    protected void willStart() {
        Logger.log("Job: willStart");
    }

    @Override
    protected void didStart() {
        Logger.log("Job: didStart");
    }

    @Override
    protected void didFinish() {
        Logger.log("Job: didFinish");
    }

    @Override
    protected void willPause() {
        Logger.log("Job: willPause");
    }

    @Override
    protected void didPause() {
        Logger.log("Job: didPause");
    }

    @Override
    protected void didInterrupt() {
        Logger.log("Job: didInterrupt");
    }

    @Override
    protected void willStop() {
        Logger.log("Job: willStop");
    }

    @Override
    protected void didStop() {
        Logger.log("Job: didStop");
        Exception error = error();
        if (error != null) {
            Logger.log("Has error = " + error.getClass().getSimpleName());
        }
    }

    @Override
    protected void willEject() {
        Logger.log("Job: willEject");
    }

}

class Demo {

    static final String customQueueTag = "CUSTOM_QUEUE_TAG";

    static boolean thrownError = false;

    static final Actable action1 = new Actable() {
        @Override
        public void act() throws Exception {
            Logger.log("Action 1 begin.......");
            Thread.sleep(1000);
            Logger.log("Action 1 end.......");
        }
    };

    static final Actable action2 = new Actable() {
        @Override
        public void act() throws Exception {
            Logger.log("Action 2 begin.......");
            Thread.sleep(2000);
            Logger.log("Action 2 end.......");
        }
    };

    static final Actable action2WithError = new Actable() {
        @Override
        public void act() throws Exception {
            Logger.log("Action 2 begin.......");
            Thread.sleep(2000);
            if (!thrownError) {
                thrownError = true;
                throw new ErrorA();
            }
            Logger.log("Action 2 end.......");
        }
    };

    static final Actable action3 = new Actable() {
        @Override
        public void act() throws Exception {
            Logger.log("Action 3 begin.......");
            Thread.sleep(3000);
            Logger.log("Action 3 end.......");
        }
    };

    static final Actable action4 = new Actable() {
        @Override
        public void act() throws Exception {
            Logger.log("Action 4 begin.......");
            Thread.sleep(4000);
            Logger.log("Action 4 end.......");
        }
    };

    static final ActableO<TargetA> action1ForTargetA = new ActableO<TargetA>() {
        @Override
        public void act(TargetA object) throws Exception {
            Logger.log("Action 1 for Target A");
        }
    };

    static final ActableO<TargetA> action2ForTargetA = new ActableO<TargetA>() {
        @Override
        public void act(TargetA object) throws Exception {
            Logger.log("Action 2 for Target A");
        }
    };

    static final ActableO<TargetB> action1ForTargetB = new ActableO<TargetB>() {
        @Override
        public void act(TargetB object) throws Exception {
            Logger.log("Action 1 for Target B");
        }
    };

    static final ActableO<TargetB> action2ForTargetB = new ActableO<TargetB>() {
        @Override
        public void act(TargetB object) throws Exception {
            Logger.log("Action 2 for Target B");
        }
    };

    static final Action<EventA> action1ForEventA = new Action<>(new ActableO<EventA>() {
        @Override
        public void act(EventA event) {
            Logger.log("Action 1 for Event A");
        }
    });

    static final Action<EventA> action2ForEventA = new Action<>(new ActableO<EventA>() {
        @Override
        public void act(EventA event) {
            Logger.log("Action 2 for Event A");
        }
    });

    static final Action<EventB> action1ForEventB = new Action<>(new ActableO<EventB>() {
        @Override
        public void act(EventB event) {
            Logger.log("Action 1 for Event B");
        }
    });

    static final Action<EventB> action2ForEventB = new Action<>(new ActableO<EventB>() {
        @Override
        public void act(EventB event) {
            Logger.log("Action 2 for Event B");
        }
    });

}

enum TargetA {
    INSTANCE
}

enum TargetB {
    INSTANCE
}

class EventA {}

class EventB {}

class ErrorA extends Exception {}
