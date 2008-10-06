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
/*VCSID=16e7ba50-dc75-48a9-a1ca-d3f84ad57d05*/
package com.sun.max.tele.field;

import com.sun.max.program.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.prototype.*;
import com.sun.max.vm.type.*;

/**
 * @author Bernd Mathiske
 */
public abstract class TeleFieldAccess {

    public static FieldActor findFieldActor(Class holder, String name) {
        final ClassActor classActor = PrototypeClassLoader.PROTOTYPE_CLASS_LOADER.mustMakeClassActor(JavaTypeDescriptor.forJavaClass(holder));
        return classActor.findFieldActor(SymbolTable.makeSymbol(name));
    }

    private final FieldActor _fieldActor;

    public FieldActor fieldActor() {
        return _fieldActor;
    }

    protected TeleFieldAccess(Class holder, String name, Kind kind) {
        _fieldActor = findFieldActor(holder, name);
        ProgramError.check(_fieldActor != null, "could not find field: " + name + " in class: " + holder);
        ProgramError.check(_fieldActor.kind() == kind, "field has wrong kind: " + name + " in class: " + holder);
    }
}
