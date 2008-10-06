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
/*VCSID=c79d810b-13f0-4c1c-9b9c-38cb7b72f5db*/
package com.sun.max.vm.run.jitTest;

import com.sun.max.annotate.*;


public class OptTest  implements InterfaceTest {
    private int _intField;

    public int getInt() {
        return _intField;
    }

    public int addInt(int i) {
        return _intField + i;
    }

    public int timesInt(int i) {
        return _intField * 8;
    }

    public void neg() {
        _intField = -_intField;
    }

    @BOOT_IMAGE_DIRECTIVE(keepUnlinked = true)
    public static int unlinkedStaticCall() {
        return 999;
    }

    @BOOT_IMAGE_DIRECTIVE(keepUnlinked = true)
    public void callUnresolved() {
        unresolved(33);
    }

    @BOOT_IMAGE_DIRECTIVE(keepUnlinked = true)
    public void unresolved(int i) {
        _intField = i;
    }

    public OptTest(int i) {
        _intField = i;
    }

    public int computeMe() {
        return 25;
    }
}
