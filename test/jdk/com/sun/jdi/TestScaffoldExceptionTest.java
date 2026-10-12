/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8352759
 * @summary Test that an exception in a later event listener reaches the driver.
 * @library /test/lib
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g TestScaffoldExceptionTest.java
 * @run main/othervm TestScaffoldExceptionTest
 */

import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

class TestScaffoldExceptionTarg {
    public static void main(String[] args) {
    }
}

public class TestScaffoldExceptionTest extends TestScaffold {
    private static final long TIMEOUT = Utils.adjustTimeout(10000);
    private final RuntimeException callbackException = new RuntimeException("Expected callback exception");
    private Process targetProcess;

    TestScaffoldExceptionTest(String[] args) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        var test = new TestScaffoldExceptionTest(args);
        try {
            test.startTests();
        } finally {
            if (test.targetProcess != null && !test.targetProcess.waitFor(TIMEOUT, TimeUnit.MILLISECONDS)) {
                test.targetProcess.destroyForcibly();
                throw new RuntimeException("Target survived TestScaffold shutdown");
            }
        }
    }

    @Override
    protected void createDefaultVMDeathRequest() {
        // Isolate the callback failure from a later VMDeath suspension.
    }

    @Override
    protected void runTests() throws Exception {
        startUp("TestScaffoldExceptionTarg");
        targetProcess = vm().process();
        ClassPrepareRequest request = eventRequestManager().createClassPrepareRequest();
        request.addClassFilter("TestScaffoldExceptionTarg");
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();

        addListener(this);
        addListener(new TargetAdapter() {
            @Override
            public void classPrepared(ClassPrepareEvent event) {
                throw callbackException;
            }
        });

        // Bound the process-exit wait so a regression still reaches shutdown().
        FutureTask<Void> wait = new FutureTask<>(() -> {
            resumeToVMDisconnect();
            return null;
        });
        Thread waiter = new Thread(wait, "waitForVMDisconnect");
        waiter.setDaemon(true);
        waiter.start();
        try {
            wait.get(TIMEOUT, TimeUnit.MILLISECONDS);
            Asserts.fail("Event handler exception was not propagated");
        } catch (ExecutionException e) {
            Asserts.assertSame(e.getCause().getCause(), callbackException, "Wrong callback exception");
        } finally {
            wait.cancel(true);
            waiter.join(TIMEOUT);
            Asserts.assertFalse(waiter.isAlive(), "Driver wait did not terminate");
        }
    }
}
