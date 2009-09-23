/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package test.com.sun.max.vm.jtrun;

/**
 * The <code>JavaTesterConfig</code> class contains configuration information about the JavaTester tests,
 * including the list of the classes and the class that contains the generated test runs.
 *
 * @author Ben L. Titzer
 */
public class JTClasses {

    public final Class[] testClasses;
    public final Class testRunClass;

    public JTClasses(Class[] testClasses, Class testRunClass) {
        this.testClasses = testClasses;
        this.testRunClass = testRunClass;
    }

    public int getTestCount() {
        return testClasses.length;
    }

    public String getTestName(int testNum) {
        return testClasses[testNum].getName();
    }
}
