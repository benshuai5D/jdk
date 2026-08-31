/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package build.tools.makejavasecurity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Builds the java.security file, including
 *
 * 1. Filter out platform-unrelated parts.
 * 2. Set the JCE jurisdiction policy directory.
 */
public class MakeJavaSecurity {

    public static void main(String[] args) throws Exception {

        if (args.length < 5) {
            System.err.println("Usage: java MakeJavaSecurity " +
                               "[input java.security file name] " +
                               "[output java.security file name] " +
                               "[openjdk target os] " +
                               "[openjdk target cpu architecture]" +
                               "[JCE jurisdiction policy directory]");

                    System.exit(1);
        }

        List<String> lines = new ArrayList<>();

        // read raw java.security
        try (FileReader fr = new FileReader(args[0]);
                BufferedReader br = new BufferedReader(fr)) {
            String line = br.readLine();
            while (line != null) {
                lines.add(line);
                line = br.readLine();
            }
        }

        // Filter out platform-unrelated ones. We only support
        // #ifdef, #ifndef, #else, and #endif. Nesting not supported (yet).
        int mode = 0;   // 0: out of block, 1: in match, 2: in non-match
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.startsWith("#endif")) {
                mode = 0;
                iter.remove();
            } else if (line.startsWith("#ifdef ")) {
                if (line.indexOf('-') > 0) {
                    mode = line.endsWith(args[2]+"-"+args[3]) ? 1 : 2;
                } else {
                    mode = line.endsWith(args[2]) ? 1 : 2;
                }
                iter.remove();
            } else if (line.startsWith("#ifndef ")) {
                if (line.indexOf('-') > 0) {
                    mode = line.endsWith(args[2]+"-"+args[3]) ? 2 : 1;
                } else {
                    mode = line.endsWith(args[2]) ? 2 : 1;
                }
                iter.remove();
            } else if (line.startsWith("#else")) {
                if (mode == 0) {
                    throw new IllegalStateException("#else not in #if block");
                }
                mode = 3 - mode;
                iter.remove();
            } else {
                if (mode == 2) iter.remove();
            }
        }

        // Update .tbd to .1, .2, etc.
        Map<String,Integer> count = new HashMap<>();
        for (int i=0; i<lines.size(); i++) {
            String line = lines.get(i);
            int index = line.indexOf(".tbd");
            if (index >= 0) {
                String prefix = line.substring(0, index);
                int n = count.getOrDefault(prefix, 1);
                count.put(prefix, n+1);
                lines.set(i, prefix + "." + n + line.substring(index+4));
            }
        }

        // Set the JCE policy value
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int index = line.indexOf("crypto.policydir-tbd");
            if (index >= 0) {
                String prefix = line.substring(0, index);
                lines.set(i, prefix + args[4]);
            }
        }

        Files.write(Paths.get(args[1]), lines);
    }
}
