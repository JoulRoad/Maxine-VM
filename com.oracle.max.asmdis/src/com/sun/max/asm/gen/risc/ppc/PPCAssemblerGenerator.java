/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.risc.ppc;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.asm.dis.ppc.*;
import com.sun.max.asm.gen.risc.*;

/**
 * The program entry point for the PowerPC assembler generator.
 */
public final class PPCAssemblerGenerator extends RiscAssemblerGenerator<RiscTemplate> {

    private PPCAssemblerGenerator() {
        super(PPCAssembly.ASSEMBLY);
    }

    @Override
    protected String getJavadocManualReference(RiscTemplate template) {
        String section = template.instructionDescription().architectureManualSection();
        if (section.indexOf("[Book ") == -1) {
            section += " [Book 1]";
        }
        return "\"PowerPC Architecture Book, Version 2.02 - Section " + section + "\"";
    }

    public static void main(String[] programArguments) {
        final PPCAssemblerGenerator generator = new PPCAssemblerGenerator();
        generator.options.parseArguments(programArguments);
        generator.generate();
    }

    @Override
    protected DisassembledInstruction generateExampleInstruction(RiscTemplate template, List<Argument> arguments) throws AssemblyException {
        return new DisassembledInstruction(new PPC32Disassembler(0, null), 0, new byte[] {0, 0, 0, 0}, template, arguments);
    }
}
