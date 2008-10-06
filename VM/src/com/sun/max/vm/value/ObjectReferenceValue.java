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
/*VCSID=54bfc0de-f623-42fb-afc3-b5b9500df667*/
package com.sun.max.vm.value;

import java.io.*;

import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.grip.*;
import com.sun.max.vm.reference.*;

/**
 * Boxed representations of object references (and null) that use a reference of type 'Object'.
 *
 * @author Bernd Mathiske
 * @author Athul Acharya
 */
public final class ObjectReferenceValue extends ReferenceValue {

    @INSPECTED
    private final Object _value;

    public static final ObjectReferenceValue NULL_OBJECT = new ObjectReferenceValue(null);

    public static ObjectReferenceValue from(Object object) {
        if (object == null) {
            return NULL_OBJECT;
        }
        return new ObjectReferenceValue(object);
    }

    private ObjectReferenceValue(Object value) {
        _value = value;
    }

    @Override
    public boolean isZero() {
        return _value == null;
    }

    @Override
    public boolean isAllOnes() {
        return asReference().isAllOnes();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            // common case of reference equality
            return true;
        }
        if (other instanceof ObjectReferenceValue) {
            final ObjectReferenceValue referenceValue = (ObjectReferenceValue) other;
            return _value == referenceValue._value;
        }
        return false;
    }

    @Override
    protected int compareSameKind(ReferenceValue other) {
        // TODO: It seems impossible to find a way to deterministically order the identities of two objects.
        //       So, Value should not implement Comparable!
        throw new IllegalArgumentException("Cannot perform comparison between values of kind " + kind());
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(_value);
    }

    @Override
    public String toString() {
        if (_value == null) {
            return "null";
        }
        if (StaticTuple.is(_value)) {
            return StaticTuple.toString(_value);
        }
        return _value.toString();
    }

    @Override
    public Object asBoxedJavaValue() {
        return _value;
    }

    @Override
    public Grip asGrip() {
        return Grip.fromJava(_value);
    }

    @Override
    public Reference asReference() {
        return Reference.fromJava(_value);
    }

    @Override
    public Object asObject() {
        return _value;
    }

    @Override
    public Object unboxObject() {
        return _value;
    }

    @Override
    public Word unboxWord() {
        return UnsafeLoophole.referenceToWord(Reference.fromJava(_value));
    }

    @Override
    public WordWidth signedEffectiveWidth() {
        return Word.width(); // TODO
    }

    @Override
    public WordWidth unsignedEffectiveWidth() {
        return Word.width(); // TODO
    }

    @Override
    public byte[] toBytes(DataModel dataModel) {
        return dataModel.toBytes(UnsafeLoophole.referenceToWord(Reference.fromJava(_value)));
    }

    @Override
    public ClassActor getClassActor() {
        return ClassActor.fromJava(_value.getClass());
    }

    @Override
    public void write(DataOutput stream) throws IOException {
        if (_value == null) {
            Word.zero().write(stream);
        } else {
            super.write(stream);
        }
    }
}
