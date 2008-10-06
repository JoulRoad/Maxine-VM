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
/*VCSID=84c4343e-3265-4a3d-b785-f00e34669084*/
package com.sun.max.vm.collect;

import com.sun.max.collect.*;
import com.sun.max.unsafe.*;

/**
 * Memoization of function results with adaptive caching.
 * 
 * Typically used to implement infrequent functional attributes.
 * 
 * @author Bernd Mathiske
 */
public final class Memoizer {
    private Memoizer() {
    }

    public interface Function<Key_Type, Value_Type> {

        public static final class Result<Value_Type> {

            private Value_Type _value;

            public Value_Type value() {
                return _value;
            }

            private Size _size;

            public Result(Value_Type value, Size size) {
                _value = value;
                _size = size;
            }
        }

        Result<Value_Type> create(Key_Type key);
    }

    public static <Key_Type, Value_Type> Mapping<Key_Type, Value_Type> create(Function<Key_Type, Value_Type> function) {
        return new FunctionResultCache<Key_Type, Value_Type>(function);
    }

    private static final class FunctionResultCache<Key_Type, Value_Type> implements Mapping<Key_Type, Value_Type> {

        private final Function<Key_Type, Value_Type> _function;

        private FunctionResultCache(Function<Key_Type, Value_Type> function) {
            _function = function;
        }

        private final Cache<Key_Type, Value_Type> _cache = Cache.createIdentityCache();

        public boolean containsKey(Key_Type key) {
            return true;
        }

        public Value_Type get(Key_Type key) {
            Value_Type value = _cache.get(key);
            if (value == null) {
                final long milliSecondsBefore = System.currentTimeMillis();
                final Function.Result<Value_Type> result = _function.create(key);
                final long costInMilliSeconds = System.currentTimeMillis() - milliSecondsBefore;
                value = result._value;
                _cache.put(key, value, result._size, Size.fromLong(costInMilliSeconds));
            }
            return value;
        }

        public int length() {
            return _cache.length();
        }

        public IterableWithLength<Key_Type> keys() {
            return _cache.keys();
        }

        public IterableWithLength<Value_Type> values() {
            return _cache.values();
        }
    }
}
