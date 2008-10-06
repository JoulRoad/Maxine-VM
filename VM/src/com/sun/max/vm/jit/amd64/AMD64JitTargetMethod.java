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
/*VCSID=ddcaed81-a0bd-4cbd-b295-356c402ec10b*/
package com.sun.max.vm.jit.amd64;

import com.sun.max.asm.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.amd64.*;
import com.sun.max.vm.jit.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.amd64.*;

/**
 * @author Bernd Mathiske
 * @author Laurent Daynes
 * @author Doug Simon
 */
public class AMD64JitTargetMethod extends JitTargetMethod implements AMD64TargetMethod {

    public AMD64JitTargetMethod(ClassMethodActor classMethodActor) {
        super(classMethodActor);
    }

    @Override
    public InstructionSet instructionSet() {
        return InstructionSet.AMD64;
    }

    @Override
    public final int registerReferenceMapSize() {
        return AMD64TargetMethod.Static.registerReferenceMapSize();
    }

    @Override
    public final void patchCallSite(int callOffset, Word callEntryPoint) {
        AMD64TargetMethod.Static.patchCallSite(this, callOffset, callEntryPoint);
    }

    @Override
    public void forwardTo(TargetMethod newTargetMethod) {
        AMD64TargetMethod.Static.forwardTo(this, newTargetMethod);
    }

    @Override
    public JitStackFrameLayout stackFrameLayout() {
        return new AMD64JitStackFrameLayout(this);
    }
}
