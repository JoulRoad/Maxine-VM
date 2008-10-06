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
/*VCSID=b843e62c-8024-45af-9d46-3fa8a7b930c3*/
package test.com.sun.max.vm.classpath;

import junit.framework.*;

public class ClasspathTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(ClasspathTest.class);
    }

    public void test_classpath() {
        final String bootclasspath = System.getProperty("sun.boot.class.path");
        assertNotNull(bootclasspath);
        System.out.println(bootclasspath);

        final String classpath = System.getProperty("java.class.path");
        assertNotNull(classpath);
        System.out.println(classpath);
    }

}
