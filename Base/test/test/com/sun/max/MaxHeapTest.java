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
/*VCSID=a7a13bc4-0302-407f-b053-e1565c1338a8*/
package test.com.sun.max;

import com.sun.max.ide.*;
import com.sun.max.program.*;


/**
 * Helps finding out whether the VM executing this test can populate object heaps with sizes beyond 4GB.
 * Run HotSpot with -verbose:gc to see heap numbers.
 *
 * @author Bernd Mathiske
 */
public class MaxHeapTest extends MaxTestCase {

    public MaxHeapTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(MaxHeapTest.class);
    }

    private final int _numberOfArrays = 128;
    private final int _leafLength = 1024 * 1024;


    public void test_max() {
        final int[][] objects = new int[_numberOfArrays][];
        int i = 0;
        try {
            while (i < _numberOfArrays) {
                objects[i] = new int[_leafLength];
                i++;
            }
        } catch (OutOfMemoryError e) {
            ProgramWarning.message("allocated " + i + " int[" + _leafLength + "] arrays");
        }
    }
}
