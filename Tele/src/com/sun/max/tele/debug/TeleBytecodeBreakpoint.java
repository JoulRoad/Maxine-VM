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
package com.sun.max.tele.debug;

import java.util.*;

import com.sun.max.collect.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.actor.member.MethodKey.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.type.*;

/**
 * Breakpoints at the beginning of bytecode instructions.
 * <br>
 * This version contains the beginning of a new implementation for bytecode
 * breakpoints that is not operational; the old design is still in effect.
 *
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public final class TeleBytecodeBreakpoint extends TeleBreakpoint {

    // TODO (mlvdv) bytecode breakpoints only supported at present for method entry.

    private static final int TRACE_VALUE = 1;

    // TODO (mlvdv) Implementation scheduled for redesign.

    private final Factory factory;

    /**
     * target breakpoints that were created in compilations of the method in the VM.
     */
    private AppendableSequence<TeleTargetBreakpoint> teleTargetBreakpoints;

    private TeleBytecodeBreakpoint(TeleVM teleVM, Factory factory, Key key) {
        super(teleVM, new TeleCodeLocation(teleVM, key), Kind.CLIENT);
        this.factory = factory;
        Trace.line(TRACE_VALUE, tracePrefix() + "new=" + this);
    }

    /**
     * @return description of the bytecode location of this breakpoint.
     */
    public Key key() {
        return teleCodeLocation().key();
    }

    // TODO (mlvdv) to deprecate with the redesign
    private void request() {
        teleVM().messenger().requestBytecodeBreakpoint(key(), key().bytecodePosition);
    }

    private void triggerDeoptimization(TeleTargetMethod teleTargetMethod) {
        // do nothing. completely broken.
    }

    /**
     * Makes this breakpoint active in the VM by locating all compilations and setting
     * target code breakpoints at the corresponding location, if that can be determined.
     */
    public void activate() {
        final Sequence<TeleTargetMethod> teleTargetMethods = TeleTargetMethod.get(teleVM(), key());
        if (teleTargetMethods.length() > 0) {
            teleTargetBreakpoints = new LinkSequence<TeleTargetBreakpoint>();
            for (TeleTargetMethod teleTargetMethod : teleTargetMethods) {
                if (teleTargetMethod instanceof TeleJitTargetMethod) {
                    final TeleJitTargetMethod teleJitTargetMethod = (TeleJitTargetMethod) teleTargetMethod;
                    final int[] bytecodeToTargetCodePositionMap = teleJitTargetMethod.bytecodeToTargetCodePositionMap();
                    final int targetCodePosition = bytecodeToTargetCodePositionMap[key().bytecodePosition];
                    final Address targetAddress = teleTargetMethod.getCodeStart().plus(targetCodePosition);
                    final TeleTargetBreakpoint teleTargetBreakpoint = teleVM().makeTargetBreakpoint(targetAddress);
                    teleTargetBreakpoint.setEnabled(true);
                    teleTargetBreakpoints.append(teleTargetBreakpoint);
                } else {
                    triggerDeoptimization(teleTargetMethod);
                }
            }
        }
        request();
    }

    /**
     * Removes any state, including in the VM, associated with this breakpoint.
     */
    private void dispose() {
        final Sequence<TeleTargetMethod> teleTargetMethods = TeleTargetMethod.get(teleVM(), key());
        if (teleTargetMethods.length() > 0) {
            for (TeleTargetMethod teleTargetMethod : teleTargetMethods) {
                if (teleTargetMethod instanceof TeleJitTargetMethod) {
                    final TeleJitTargetMethod teleJitTargetMethod = (TeleJitTargetMethod) teleTargetMethod;
                    final int[] bytecodeToTargetCodePositionMap = teleJitTargetMethod.bytecodeToTargetCodePositionMap();
                    final int targetCodePosition = bytecodeToTargetCodePositionMap[key().bytecodePosition];
                    final Address targetAddress = teleTargetMethod.getCodeStart().plus(targetCodePosition);
                    final TeleTargetBreakpoint targetBreakpoint = teleVM().getTargetBreakpoint(targetAddress);
                    if (targetBreakpoint != null) {
                        targetBreakpoint.remove();
                    }
                    // Assume for now the whole VM is stopped; there will be races to be fixed otherwise, likely with an agent thread in the VM.
                }
            }
            teleTargetBreakpoints = null;
        }
        teleVM().messenger().cancelBytecodeBreakpoint(key(), key().bytecodePosition);
        // Note that just sending a message to cancel the breakpoint request in the VM doesn't actually remove any
        // breakpoints generated by the VM in response to the original request.  Those will eventually be discovered
        // and removed remotely.
    }

    @Override
    public void remove() {
        Trace.line(TRACE_VALUE, tracePrefix() + "removing=" + this);
        dispose();
        factory.removeBreakpoint(key());
    }

    private boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean setEnabled(boolean enabled) {
        if (enabled != this.enabled) {
            this.enabled = enabled;
            if (enabled) {
                activate();
            }
            factory.announceStateChange();
            // TODO (mlvdv) disable bytecode breakpoint
            return true;
        }
        return false;
    }

    @Override
    public BreakpointCondition condition() {
        // Conditional bytecode breakpoints not supported yet
        return null;
    }

    @Override
    public void setCondition(String conditionDescriptor) {
        // TODO (mlvdv) add support for conditional bytecode breakpoints
        // Replicate the condition in each derivative target code breakpoint.
        ProgramError.unexpected("Conditional bytecode breakpoints net yet implemented");
    }

    @Override
    public String toString() {
        return "Bytecode breakpoint" + key() + " " + attributesToString();
    }

    /**
     * Describes a bytecode position in the VM,
     * i.e. indicates the exact method and byte code position.
     *
     * The method does not have to be compiled, nor even loaded yet.
     */
    public static class Key extends DefaultMethodKey {

        protected final int bytecodePosition;

        /**
         * @return bytecode position in the method.
         */
        public int position() {
            return bytecodePosition;
        }

        public Key(MethodKey methodKey) {
            this(methodKey, 0);
        }

        public Key(BytecodeLocation bytecodeLocation) {
            super(bytecodeLocation.classMethodActor);
            this.bytecodePosition = bytecodeLocation.bytecodePosition;
        }

        public Key(MethodKey methodKey, int bytecodePosition) {
            super(methodKey.holder(), methodKey.name(), methodKey.signature());
            this.bytecodePosition = bytecodePosition;
        }

        public Key(SignatureDescriptor signature, TypeDescriptor holder, Utf8Constant name, int bytecodePosition) {
            super(holder, name, signature);
            this.bytecodePosition = bytecodePosition;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            if (obj instanceof Key) {
                final Key otherKey = (Key) obj;
                return bytecodePosition == otherKey.bytecodePosition;

            }
            return false;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ bytecodePosition;
        }

        @Override
        public String toString() {
            return "{" + super.toString() + ", position=" + bytecodePosition + "}";
        }
    }

    /**
     * A factory that creates, tracks, and removes bytecode breakpoints from the VM.
     *
     * @author Michael Van De Vanter
     */
    public static class Factory extends Observable {

        private final TeleVM teleVM;
        private final TeleTargetBreakpoint.Factory teleTargetBreakpointFactory;
        private final String tracePrefix;

        /**
         * A breakpoint that interrupts the compiler just as it finishes compiling a method.  Non-null and active
         * iff there are one or more bytecode breakpoints in existence.
         */
        private TeleTargetBreakpoint compilerTargetCodeBreakpoint = null;

        public Factory(TeleVM teleVM) {
            this.tracePrefix = "[" + getClass().getSimpleName() + "] ";
            Trace.line(TRACE_VALUE, tracePrefix + "creating");
            this.teleVM = teleVM;
            this.teleTargetBreakpointFactory = teleVM.teleProcess().targetBreakpointFactory();
        }

        private final VariableMapping<Key, TeleBytecodeBreakpoint> breakpoints = HashMapping.createVariableEqualityMapping();

        /**
         * Notify all observers that there has been a state change concerning these breakpoints.
         */
        private void announceStateChange() {
            setChanged();
            notifyObservers();
        }

        /**
         * @return all bytecode breakpoints that currently exist in the VM.
         * Modification safe against breakpoint removal.
         */
        public synchronized Iterable<TeleBytecodeBreakpoint> breakpoints() {
            final AppendableSequence<TeleBytecodeBreakpoint> breakpoints = new LinkSequence<TeleBytecodeBreakpoint>();
            for (TeleBytecodeBreakpoint teleBytecodeBreakpoint : this.breakpoints.values()) {
                breakpoints.append(teleBytecodeBreakpoint);
            }
            return breakpoints;
        }

        /**
         * @return the number of bytecode breakpoints that currently exist in the VM.
         */
        public synchronized int size() {
            return breakpoints.length();
        }

        /**
         * @param key description of a bytecode position in a method
         * @return a breakpoint set at the position, null if none.
         */
        public synchronized TeleBytecodeBreakpoint getBreakpoint(Key key) {
            return breakpoints.get(key);
        }

        private TeleBytecodeBreakpoint createBreakpoint(Key key) {
            if (breakpoints.length() == 0) {
                // TODO (mlvdv) new bytecode breakpoint implementation
                //createCompilerBreakpoint();
            }
            final TeleBytecodeBreakpoint breakpoint = new TeleBytecodeBreakpoint(teleVM, this, key);
            breakpoints.put(key, breakpoint);
            Trace.line(TRACE_VALUE, tracePrefix + "new=" + breakpoint);
            announceStateChange();
            return breakpoint;
        }

        /**
         * @param key description of a bytecode position in a method
         * @return a possibly new, enabled bytecode breakpoint
         */
        public synchronized TeleBytecodeBreakpoint makeBreakpoint(Key key) {
            TeleBytecodeBreakpoint breakpoint = getBreakpoint(key);
            if (breakpoint == null) {
                breakpoint = createBreakpoint(key);
            }
            breakpoint.setEnabled(true);
            return breakpoint;
        }

        /**
         * Removes a breakpoint at the described position, if one exists.
         * @param key description of a bytecode position in a method
         */
        private synchronized void removeBreakpoint(Key key) {
            breakpoints.remove(key);
            if (breakpoints.length() == 0) {
                // TODO (mlvdv) new bytecode breakpoint implementation
                //removeCompilerBreakpoint();
            }
            announceStateChange();
        }

        private void createCompilerBreakpoint() {
            assert compilerTargetCodeBreakpoint == null;
            final TeleClassMethodActor teleClassMethodActor = teleVM.teleMethods().BytecodeBreakpointMessage_compilationFinished.teleClassMethodActor();
            final TeleTargetMethod javaTargetMethod = teleClassMethodActor.getJavaTargetMethod(0);
            final Address callEntryPoint = javaTargetMethod.callEntryPoint();
            ProgramError.check(!callEntryPoint.isZero());
            compilerTargetCodeBreakpoint = teleTargetBreakpointFactory.makeSystemBreakpoint(callEntryPoint);
            compilerTargetCodeBreakpoint.setTriggerEventHandler(new VMTriggerEventHandler() {

                @Override
                public boolean handleTriggerEvent(TeleNativeThread teleNativeThread) {
                    System.out.println("compilerTargetCodeBreakpoint hit, resuming");
                    return false;
                }

            });
            Trace.line(TRACE_VALUE, tracePrefix + "new compiler breakpoint=" + compilerTargetCodeBreakpoint);
        }

        private void removeCompilerBreakpoint() {
            assert compilerTargetCodeBreakpoint != null;
            Trace.line(TRACE_VALUE, tracePrefix + "removing compiler breakpoint=" + compilerTargetCodeBreakpoint);
            compilerTargetCodeBreakpoint.remove();
            compilerTargetCodeBreakpoint = null;
        }

    }
}
