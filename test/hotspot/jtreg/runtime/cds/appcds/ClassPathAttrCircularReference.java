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
 *
 */

/*
 * @test
 * @summary CDS dumping should not loop when JAR manifest Class-Path entries form a circular reference through equivalent paths
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib
 * @run driver/timeout=60 ClassPathAttrCircularReference
 */

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class ClassPathAttrCircularReference {
    public static void main(String[] args) throws Exception {
        Path jarDir = Paths.get(CDSTestUtils.getOutputDir(), "cpattr-circular");
        Files.createDirectories(jarDir);

        Path jarA = jarDir.resolve("A.jar");
        Path jarB = jarDir.resolve("B.jar");
        createJar(jarA, "./B.jar");
        createJar(jarB, "A.jar");

        Path jarADot = jarDir.resolve(".").resolve("A.jar");
        Path jarBDot = jarDir.resolve(".").resolve("B.jar");
        if (!Files.isSameFile(jarA, jarADot)) {
            throw new RuntimeException(jarA + " and " + jarADot + " should refer to the same file");
        }
        if (!Files.isSameFile(jarB, jarBDot)) {
            throw new RuntimeException(jarB + " and " + jarBDot + " should refer to the same file");
        }

        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String archive = TestCommon.getNewArchiveName("cpattr-circular");
        ProcessBuilder pb = CDSTestUtils.makeBuilder(java,
                                                     "-Xshare:dump",
                                                     "-XX:SharedArchiveFile=" + archive,
                                                     "-Xlog:class+path=info",
                                                     "-cp", jarA.toString());
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dump");

        output.shouldHaveExitValue(0);
        output.shouldContain("path [2] =");
        output.shouldNotContain("path [3] =");
    }

    private static void createJar(Path jar, String classPath) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.CLASS_PATH, classPath);

        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out, manifest)) {
            // No class entries are needed. CDS only needs the manifest Class-Path.
        }
    }
}
