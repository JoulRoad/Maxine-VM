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
/*VCSID=59fb22b7-7883-441a-9a40-0fc9be68f0aa*/
package com.sun.max.vm.stack;

import com.sun.max.unsafe.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.snippet.*;

/**
 *
  * @author Laurent Daynes
  */
public class StaticTrampolineContext implements StackFrameVisitor {

    private static Pointer _trampolineInstructionPointer;

    boolean _foundFirstTrampolineFrame;
    Pointer _stackPointer;
    Pointer _instructionPointer;

    public boolean visitFrame(StackFrame stackFrame) {
        if (stackFrame.isTopFrame() || stackFrame.isAdapter()) {
            return true;
        }
        final Pointer instructionPointer = stackFrame.instructionPointer();
        if (_trampolineInstructionPointer.isZero() && Code.codePointerToTargetMethod(instructionPointer).classMethodActor().holder().toJava() == StaticTrampoline.class) {
            _trampolineInstructionPointer = instructionPointer;
        }
        if (instructionPointer == _trampolineInstructionPointer) {
            _foundFirstTrampolineFrame = true;
        } else if (_foundFirstTrampolineFrame) {
            // This is the first non-adapter frame before the trampoline.
            _stackPointer = stackFrame.stackPointer();
            _instructionPointer = instructionPointer;
            return false;
        }
        return true;
    }

    public Pointer stackPointer() {
        return _stackPointer;
    }

    public Pointer instructionPointer() {
        return _instructionPointer;
    }

}
