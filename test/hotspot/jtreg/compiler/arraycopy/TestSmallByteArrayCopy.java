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

/*
 * @test
 * @bug 8365779
 * @summary Verify wide loads and stores for aligned small byte array copies
 * @requires vm.compiler2.enabled & (vm.bits == "64")
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @run driver ${test.main.class}
 */

package compiler.arraycopy;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import java.util.Arrays;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

public class TestSmallByteArrayCopy {
    private static final int ALIGNED = (int) ((8 - (Unsafe.ARRAY_BYTE_BASE_OFFSET & 7)) & 7);
    private int seed;

    public static void main(String[] args) {
        new TestFramework()
                .addFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                          "-XX:ArrayOperationPartialInlineSize=0")
                .addScenarios(new Scenario(0), new Scenario(1, "-XX:ArrayCopyLoadStoreMaxElem=0"))
                .start();
    }

    @Test
    @IR(phase = CompilePhase.AFTER_MACRO_EXPANSION,
        applyIf = {"ArrayCopyLoadStoreMaxElem", "8"},
        counts = {IRNode.LOAD_L, "2", IRNode.STORE_L, "2"},
        failOn = {IRNode.CALL_LEAF_NO_FP})
    @IR(phase = CompilePhase.AFTER_MACRO_EXPANSION,
        applyIf = {"ArrayCopyLoadStoreMaxElem", "0"},
        counts = {IRNode.CALL_LEAF_NO_FP, "1"},
        failOn = {IRNode.LOAD_L, IRNode.STORE_L})
    public static void copy16(byte[] source, byte[] target) {
        System.arraycopy(source, ALIGNED, target, ALIGNED, 16);
    }

    @Test
    @IR(phase = CompilePhase.AFTER_MACRO_EXPANSION,
        applyIf = {"ArrayCopyLoadStoreMaxElem", "8"},
        counts = {IRNode.LOAD_L, "2", IRNode.STORE_L, "2"},
        failOn = {IRNode.CALL_LEAF_NO_FP})
    @IR(phase = CompilePhase.AFTER_MACRO_EXPANSION,
        applyIf = {"ArrayCopyLoadStoreMaxElem", "0"},
        counts = {IRNode.CALL_LEAF_NO_FP, "1"},
        failOn = {IRNode.LOAD_L, IRNode.STORE_L})
    public static void copyBackward(byte[] array) {
        System.arraycopy(array, ALIGNED, array, ALIGNED + 8, 16);
    }

    @Test
    @IR(phase = CompilePhase.AFTER_MACRO_EXPANSION,
        applyIf = {"ArrayCopyLoadStoreMaxElem", "8"},
        counts = {IRNode.LOAD_L, "2", IRNode.STORE_L, "2"},
        failOn = {IRNode.CALL_LEAF_NO_FP})
    @IR(phase = CompilePhase.AFTER_MACRO_EXPANSION,
        applyIf = {"ArrayCopyLoadStoreMaxElem", "0"},
        counts = {IRNode.CALL_LEAF_NO_FP, "1"},
        failOn = {IRNode.LOAD_L, IRNode.STORE_L})
    public static void copyForward(byte[] array) {
        System.arraycopy(array, ALIGNED + 8, array, ALIGNED, 16);
    }

    @Run(test = {"copy16", "copyBackward", "copyForward"})
    public void run() {
        for (int kind = 0; kind < 3; kind++) {
            byte[] source = pattern(seed++);
            byte[] target = kind == 0 ? pattern(seed + 71) : source;
            byte[] snapshot = source.clone();
            byte[] expected = target.clone();
            int from = ALIGNED + (kind == 2 ? 8 : 0);
            int to = ALIGNED + (kind == 1 ? 8 : 0);
            for (int i = 0; i < 16; i++) {
                expected[to + i] = snapshot[from + i];
            }
            switch (kind) {
                case 0 -> copy16(source, target);
                case 1 -> copyBackward(target);
                case 2 -> copyForward(target);
                default -> throw new AssertionError("Unexpected copy case");
            }
            Asserts.assertEQ(Arrays.mismatch(expected, target), -1, "Copy mismatch, case " + kind);
            if (kind == 0) {
                Asserts.assertEQ(Arrays.mismatch(snapshot, source), -1, "Source modified");
            }
        }
    }

    private static byte[] pattern(int seed) {
        byte[] array = new byte[64];
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) (seed * 19 + i * 37);
        }
        return array;
    }
}
