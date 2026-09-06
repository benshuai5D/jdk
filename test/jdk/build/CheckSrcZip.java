/*
 * Copyright (c) 2026, Yunbo Zhang. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/*
 * @test
 * @bug 8320228
 * @summary Check that src.zip contains sources and doc-files resources
 * @run main CheckSrcZip
 */
public class CheckSrcZip {
    private static final List<String> EXPECTED_ENTRIES = List.of(
            "java.base/java/lang/Object.java",
            "java.base/java/lang/classfile/snippet-files/PackageSnippets.java",
            "java.base/java/lang/doc-files/ValueBased.html",
            "java.desktop/java/awt/doc-files/BorderLayout-1.png",
            "java.desktop/java/awt/doc-files/Button-1.gif",
            "java.desktop/java/awt/doc-files/FocusCycle.svg",
            "java.desktop/javax/swing/text/doc-files/View-layout.jpg",
            "java.desktop/javax/swing/plaf/synth/doc-files/synth.dtd"
    );

    private static final List<String> UNEXPECTED_ENTRIES = List.of(
            "java.desktop/javax/swing/plaf/basic/icons/JavaCup16.png"
    );

    public static void main(String[] args) throws Exception {
        Path srcZip = Path.of(System.getProperty("test.jdk"), "lib", "src.zip");
        try (ZipFile zip = new ZipFile(srcZip.toFile())) {
            for (String name : EXPECTED_ENTRIES) {
                if (zip.getEntry(name) == null) {
                    throw new AssertionError("Missing entry in src.zip: " + name);
                }
            }

            for (String name : UNEXPECTED_ENTRIES) {
                if (zip.getEntry(name) != null) {
                    throw new AssertionError("Unexpected entry in src.zip: " + name);
                }
            }
        }
    }
}
