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
 *
 */

/*
 * @test
 * @bug 8388385
 * @summary CDS dumping should not loop on circular JAR manifest Class-Path entries
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib
 * @run driver/timeout=60 ClassPathAttrCircularReference
 */

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class ClassPathAttrCircularReference {
    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    public static void main(String[] args) throws Exception {
        Path jarDir = USER_DIR.resolve("cpattr-circular");
        Files.createDirectories(jarDir);

        Path jarA = jarDir.resolve("A.jar");
        Path jarB = jarDir.resolve("B.jar");
        createJar(jarA, "./B.jar");
        createJar(jarB, "A.jar");

        if (!Files.isSameFile(jarA, jarDir.resolve(".").resolve("A.jar"))) {
            throw new RuntimeException("Equivalent paths must refer to the same JAR");
        }

        String java = JDKToolFinder.getJDKTool("java");
        Path archive = jarDir.resolve("cpattr-circular.jsa");
        ProcessBuilder pb = CDSTestUtils.makeBuilder(
                java,
                "-Xshare:dump",
                "-XX:SharedArchiveFile=" + archive,
                "-Xlog:class+path=info",
                "-cp", "A.jar");
        pb.directory(jarDir.toFile());

        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "dump");
        output.shouldHaveExitValue(0);
        output.shouldContain("path [2] =");
        output.shouldNotContain("path [3] =");
    }

    private static void createJar(Path jar, String classPath) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.CLASS_PATH, classPath);

        try (OutputStream stream = Files.newOutputStream(jar);
             JarOutputStream jarStream = new JarOutputStream(stream, manifest)) {
        }
    }
}
