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
/*VCSID=3712e75b-00a2-4507-8b39-219c7f7f7fde*/
/*
 * @Harness: java
 * @Runs: -9223372036854775808L=true; -6L=true; -5L=false; -4L=false; -1L=false; 0L=false; 1L=false; 2L=false; 9223372036854775807L=false
 */
package test.lang;

public final class Long_less03 {
    private Long_less03() {
    }

    public static boolean test(long i) {
        if (i < -5L) {
            return true;
        }
        return false;
    }
}
