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
/*VCSID=3c9be09b-ab6e-4a35-9a44-7bddec9547fd*/
package com.sun.max.vm.verifier.types;

import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.type.*;

/**
 * Represents array types of dimension 1 for which the corresponding ArrayClassActor already exists.
 * That is, {@linkplain #resolve() resolving} this verification type is guaranteed not to cause class loading.
 *
 * @author Doug Simon
 */
public class ResolvedArrayType extends ArrayType implements ResolvedType {

    private final ArrayClassActor _arrayClassActor;

    ResolvedArrayType(ArrayClassActor arrayClassActor, VerificationType componentType) {
        super(arrayClassActor.typeDescriptor(), null);
        assert JavaTypeDescriptor.getArrayDimensions(arrayClassActor.typeDescriptor()) == 1;
        _arrayClassActor = arrayClassActor;
        _componentType = componentType;
    }

    @Override
    public VerificationType elementType() {
        return _componentType;
    }

    @Override
    public ClassActor resolve() {
        return _arrayClassActor;
    }
}
