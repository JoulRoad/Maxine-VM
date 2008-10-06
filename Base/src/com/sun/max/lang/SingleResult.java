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
/*VCSID=cb22a30a-e45a-4033-8ca5-b136b31c91dd*/
package com.sun.max.lang;

import com.sun.max.program.*;

/**
 * @author Bernd Mathiske
 */
public class SingleResult<Object_Type> extends MutableInnerClassGlobal<Object_Type> {

    public SingleResult() {
        super();
    }

    @Override
    public Object_Type value() {
        ProgramError.check(super.value() != null, "no result");
        return super.value();
    }

    @Override
    public void setValue(Object_Type result) {
        ProgramError.check(super.value() == null, "multiple results when only one was expected: " + result + " and " + super.value());
        super.setValue(result);
    }
}
