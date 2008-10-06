/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
/*VCSID=9bb58b45-dfed-4f17-8ca2-8fe76b8ebaf3*/
package com.sun.max.vm.compiler.b.c.d.e.amd64;

import com.sun.max.asm.*;
import com.sun.max.lang.*;
import com.sun.max.platform.*;
import com.sun.max.platform.Platform.*;
import com.sun.max.program.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.b.c.d.e.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.compiler.eir.amd64.*;

/**
 * @author Bernd Mathiske
 */
public class BcdeAMD64Compiler extends BcdeCompiler<AMD64EirGenerator> implements AMD64EirGeneratorScheme {

    private DirToAMD64EirTranslator _dirToEirTranslator;

    public AMD64EirGenerator eirGenerator() {
        return _dirToEirTranslator;
    }

    public BcdeAMD64Compiler(VMConfiguration vmConfiguration) {
        super(vmConfiguration);

        vmConfiguration.platform().inspect(new PlatformInspectorAdapter() {

            private String incompatibilityMessage(String s) {
                return name() + " compiler incompatible with " + s;
            }

            @Override
            public void inspectEndianness(Endianness endianness) {
                ProgramError.check(endianness == Endianness.LITTLE, incompatibilityMessage(endianness + " endian platform"));
            }

            @Override
            public void inspectInstructionSet(InstructionSet isa) {
                ProgramError.check(isa == InstructionSet.AMD64, incompatibilityMessage(isa + " instruction set"));
            }

            @Override
            public void inspectProcessorModel(ProcessorModel cpu) {
                ProgramError.check(cpu == ProcessorModel.AMD64, incompatibilityMessage(cpu + " processor model"));
            }

            @Override
            public void inspectWordWidth(WordWidth wordWidth) {
                ProgramError.check(wordWidth == WordWidth.BITS_64, incompatibilityMessage(wordWidth + " word width"));
            }
        });
        _dirToEirTranslator = new DirToAMD64EirTranslator(this);
    }

    @Override
    protected Class<? extends BuiltinVisitor> builtinTranslationClass() {
        return DirToAMD64EirBuiltinTranslation.class;
    }

}
