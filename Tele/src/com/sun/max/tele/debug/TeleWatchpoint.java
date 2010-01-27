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

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.sun.max.collect.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.layout.Layout.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;

/**
 * <strong>Watchpoints</strong>.
 * <br>
 * A watchpoint triggers <strong>after</strong> a specified event has occurred: read, write, or exec.  So-called "before"
 * watchpoints are not supported.
 * <br>
 * Watchpoint creation may fail for platform-specific reasons, for example if watchpoints are not supported at all, or are
 * only supported in limited numbers, or only permitted in certain sizes or locations.
 * <br>
 * A new watchpoint is "alive" and remains so until disposed (deleted), at which time it become permanently inert.  Any attempt
 * to enable or otherwise manipulate a disposed watchpoint will cause a ProgramError to be thrown.
 * <br>
 * A watchpoint is by definition "enabled" (client concept) if it is alive and one or more of the three trigger settings
 * is true:  <strong>trapOnRead</strong>, <strong>trapOnWrite</strong>, or <strong>trapOnExec</strong>.
 * If none is true, then the watchpoint is by definition "disabled" and can have no effect on VM execution.
 * <br>
 * A watchpoint is "active" (implementation concept) if it has been installed in the process running the VM, something that may
 * happen when when it is enabled.  If a watchpoint becomes disabled, it will be deactivated (removed from the process).
 * A watchpoint may also be deactivated/reactivated transparently to the client for implementation purposes.
 * <br>
 * A watchpoint with <strong>enabledDuringGC</strong> set to false will be effectively disabled during any period of time when
 * the VM is performing GC.  In practice, the watchpoint may trigger if an event takes place during GC, but execution will then
 * resume silently when it is determined that GC is underway.  This is true whether the watchpoint is relocatable or not.
 * <br>
 * A <strong>relocatable</strong> watchpoint is set on a location that is part of an object's representation.  Such a watchpoint
 * follows the object should its representation be moved during GC to a different location. The life cycle of a relocatable watchpoint
 * depends on the state of the object when first created, on the treatment of the object by the GC in the VM, and by the timing
 * in which the Inspector is able to update its state in response to GC actions.
 * <br>
 * A watchpoint may only be created on an object known to the inspector as live (neither collected/dead nor forwarded/obsolete).
 * Attempting to set a watchpoint on an object known to the inspector to be not live will cause a ProgramError to be thrown.
 * <br>
 * A relocatable watchpoint associated with an object that is eventually determined to have been collected will be disposed and
 * replaced with a non-relocatable watchpoint covering the same memory region.
 *
 * @author Michael Van De Vanter
 */
public abstract class TeleWatchpoint extends RuntimeMemoryRegion implements VMTriggerEventHandler, MaxWatchpoint {

    // TODO (mlvdv) Consider a response when user tries to set a watchpoint on first header word.  May mean that
    // TODO (mlvdv) there can be multiple watchpoints at a location.  Work through the use cases.
    // TODO (mlvdv) Note that system watchpoint code does not check for too many or for overlap.

    /**
     * Distinguishes among uses for watchpoints,
     * independently of how the location is specified.
     */
    private enum WatchpointKind {

        /**
         * A watchpoint created on behalf of a client external to the {@link TeleVM}.  Such
         * a watchpoint is presumed to be managed completely by the client:  creation/deletion,
         * enable/disable etc.  Only client watchpoints are visible to the client in ordinary use.
         */
        CLIENT,

        /**
         * A watchpoint created by one of the services int he {@link TeleVM}, generally in order
         * to catch certain events in the VM so that state can be synchronized for
         * some purpose.  Presumed to be managed completely by the service using it.  These
         * are generally not visible to clients.
         * <br>
         * Not relocatable.
         */
        SYSTEM;
    }

    private static final int TRACE_VALUE = 1;
    private final String tracePrefix;

    private final WatchpointKind kind;

    /**
     * Watchpoints factory.
     */
    protected final Factory factory;

    /**
     * Is this watchpoint still alive (not yet disposed) and available for activation/deactivation?
     * This is true from the creation of the watchpoint until it is disposed, at which event
     * it becomes permanently false and it cannot be used.
     */
    private boolean alive = true;

    /**
     * Is this watchpoint currently active in the process?
     * <br>
     * This is an implementation issue, which should not be visible to clients.
     * <br>
     * Only "live" watchpoints may be activated.
     */
    private boolean active = false;

    /**
     * Watchpoint configuration.
     */
    private WatchpointSettings settings;

    /**
     * Stores data read from the memory covered by watchpoint.
     */
    private byte[] memoryCache;

    private VMTriggerEventHandler triggerEventHandler = VMTriggerEventHandler.Static.ALWAYS_TRUE;

    private TeleWatchpoint(WatchpointKind kind, Factory factory, String description, Address start, Size size, WatchpointSettings settings) {
        super(start, size);
        setDescription(description);
        this.kind = kind;
        this.factory = factory;
        this.settings = settings;
        this.tracePrefix = "[" + getClass().getSimpleName() + "] ";
    }

    private TeleWatchpoint(WatchpointKind kind, Factory factory, String description, MemoryRegion memoryRegion, WatchpointSettings settings) {
        this(kind, factory, description, memoryRegion.start(), memoryRegion.size(), settings);
    }

    protected final String tracePrefix() {
        return tracePrefix;
    }

    public String getDescription() {
        return description();
    }

    @Override
    public final boolean equals(Object o) {
        // For the purposes of the collection, define ordering and equality in terms of start location only.
        if (o instanceof TeleWatchpoint) {
            final TeleWatchpoint teleWatchpoint = (TeleWatchpoint) o;
            return start().equals(teleWatchpoint.start());
        }
        return false;
    }

    public final boolean setTrapOnRead(boolean trapOnRead) {
        assert alive;
        this.settings = new WatchpointSettings(trapOnRead, settings.trapOnWrite, settings.trapOnExec, settings.enabledDuringGC);
        return reset();
    }

    public final boolean setTrapOnWrite(boolean trapOnWrite) {
        assert alive;
        this.settings = new WatchpointSettings(settings.trapOnRead, trapOnWrite, settings.trapOnExec, settings.enabledDuringGC);
        return reset();
    }

    public final boolean setTrapOnExec(boolean trapOnExec) {
        assert alive;
        this.settings = new WatchpointSettings(settings.trapOnRead, settings.trapOnWrite, trapOnExec, settings.enabledDuringGC);
        return reset();
    }

    public final boolean setEnabledDuringGC(boolean enabledDuringGC) {
        assert alive;
        this.settings = new WatchpointSettings(settings.trapOnRead, settings.trapOnWrite, settings.trapOnExec, enabledDuringGC);
        if (enabledDuringGC && factory.teleVM().isInGC() && !active) {
            setActive(true);
        }
        return reset();
    }

    public final WatchpointSettings getSettings() {
        return settings;
    }

    public final boolean isEnabled() {
        return alive && (settings.trapOnRead || settings.trapOnWrite || settings.trapOnExec);
    }

    public boolean dispose() {
        assert alive;
        if (active) {
            setActive(false);
        }
        final boolean isDisposed =  factory.removeWatchpoint(this);
        if (isDisposed) {
            alive = false;
        }
        return isDisposed;
    }

    public final boolean handleTriggerEvent(TeleNativeThread teleNativeThread) {
        assert alive;
        assert teleNativeThread.state() == TeleNativeThread.ThreadState.WATCHPOINT;
        Trace.begin(TRACE_VALUE, tracePrefix() + "handling trigger event for " + this);
        if (factory.teleVM().isInGC() && !settings.enabledDuringGC) {
            // Ignore the event if the VM is in GC and the watchpoint is not to be enabled during GC.
            // This is a lazy policy that avoids the need to interrupt the VM every time GC starts.
            // Just in case such a watchpoint would trigger repeatedly during GC, however, deactivate
            // it now (at first trigger) for the duration of the GC.  All such watchpoints will be
            // reactivated at the conclusion of GC, when it is necessary to interrupt the VM anyway.
            setActive(false);
            Trace.end(TRACE_VALUE, tracePrefix() + "handling trigger event (IGNORED) for " + this);
            return false;
        }
        final boolean handleTriggerEvent = triggerEventHandler.handleTriggerEvent(teleNativeThread);
        Trace.end(TRACE_VALUE, tracePrefix() + "handling trigger event for " + this);
        return handleTriggerEvent;
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("{").append(kind.toString());
        if (!alive) {
            sb.append("(DELETED)");
        }
        sb.append(", ").append(isEnabled() ? "enabled" : "disabled");
        sb.append(", ").append(isActive() ? "active" : "inactive");
        sb.append(", 0x").append(start().toHexString());
        sb.append(", size=").append(size().toString());
        sb.append(", \"").append(getDescription()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    protected final boolean isAlive() {
        return alive;
    }

    protected final boolean isActive() {
        return active;
    }

    /**
     * Assigns to this watchpoint a  handler for events triggered by this watchpoint.  A null handler
     * is equivalent to there being no handling action and a return of true (VM execution should halt).
     *
     * @param triggerEventHandler handler for VM execution events triggered by this watchpoint.
     */
    protected final void setTriggerEventHandler(VMTriggerEventHandler triggerEventHandler) {
        this.triggerEventHandler =
            (triggerEventHandler == null) ? VMTriggerEventHandler.Static.ALWAYS_TRUE : triggerEventHandler;
    }

    /**
     * Reads and stores the contents of VM memory in the region of the watchpoint.
     *
     * Future usage: e.g. for conditional Watchpoints
     */
    private void updateMemoryCache() {
        if (memoryCache == null || memoryCache.length != size.toInt()) {
            memoryCache = new byte[size.toInt()];
        }
        try {
            memoryCache = factory.teleVM().dataAccess().readFully(start, size.toInt());
        } catch (DataIOError e) {
            // Must be a watchpoint in an address space that doesn't (yet?) exist in the VM process.
            memoryCache = null;
        }
    }

    /**
     * Change the activation state of the watchpoint in the VM.
     *
     * @param active the desired activation state
     * @return whether the change succeeded
     * @throws ProgramError if requested state same as current state
     */
    private boolean setActive(boolean active) {
        assert alive;
        if (active) {  // Try to activate
            ProgramError.check(!this.active, "Attempt to activate an active watchpoint:", this);
            if (factory.teleProcess.activateWatchpoint(this)) {
                this.active = true;
                Trace.line(TRACE_VALUE, tracePrefix() + "Watchpoint activated: " + this);
                return true;
            } else {
                ProgramWarning.message("Failed to activate watchpoint: " + this);
                return false;
            }
        } else { // Try to deactivate
            ProgramError.check(this.active, "Attempt to deactivate an inactive watchpoint:", this);
            if (factory.teleProcess.deactivateWatchpoint(this)) {
                this.active = false;
                Trace.line(TRACE_VALUE, tracePrefix() + "Watchpoint deactivated " + this);
                return true;
            }
            ProgramWarning.message("Failed to deactivate watchpoint: " + this);
            return false;
        }
    }

    /**
     * Resets a watchpoint by deactivating it and then reactivating at the same
     * location.  This should be done when any settings change.
     *
     * @return true if reset was successful
     */
    private boolean reset() {
        assert alive;
        if (active) {
            if (!setActive(false)) {
                ProgramWarning.message("Failed to reset watchpoint: " + this);
                return false;
            }
            if (!setActive(true)) {
                ProgramWarning.message("Failed to reset and install watchpoint: " + this);
                return false;
            }
        }
        Trace.line(TRACE_VALUE, tracePrefix() + "Watchpoint reset " + this);
        factory.updateAfterWatchpointChanges();
        return true;
    }

    /**
     * Relocates a watchpoint by deactivating it and then reactivating
     * at a new start location.
     * <br>
     * Note that the location of a watchpoint should <strong>only</strong> be changed
     * via this method, since it must first be deactivated at its old location before the
     * new location is set.
     *
     * @param newAddress a new starting location for the watchpoint
     * @return true if reset was successful
     */
    protected final boolean relocate(Address newAddress) {
        assert newAddress != null;
        assert alive;
        if (active) {
            // Must deactivate before we change the location
            if (!setActive(false)) {
                ProgramWarning.message("Failed to reset watchpoint: " + this);
                return false;
            }
            setStart(newAddress);
            if (!setActive(true)) {
                ProgramWarning.message("Failed to reset and install watchpoint: " + this);
                return false;
            }
        } else {  // not active
            setStart(newAddress);
        }
        Trace.line(TRACE_VALUE, tracePrefix() + "Watchpoint reset " + start().toHexString());
        factory.updateAfterWatchpointChanges();
        return true;
    }

    /**
     * Perform any updates on watchpoint state at the conclusion of a GC.
     */
    protected void updateAfterGC() {
        if (isEnabled() && !active) {
            // This watchpoint was apparently deactivated during GC because
            // it is not to be enabled during GC.
            setActive(true);
        }
    }

    /**
     * A watchpoint for a specified, fixed memory region.
     */
    private static final class TeleRegionWatchpoint extends TeleWatchpoint {

        public TeleRegionWatchpoint(WatchpointKind kind, Factory factory, String description, MemoryRegion memoryRegion, WatchpointSettings settings) {
            super(kind, factory, description, memoryRegion.start(), memoryRegion.size(), settings);
        }

        public boolean isRelocatable() {
            return false;
        }

        public TeleObject getTeleObject() {
            return null;
        }
    }


    /**
     * A watchpoint for the memory holding a {@linkplain VmThreadLocal thread local variable}.
     *
     * @see VmThreadLocal
     */
    private static final class TeleVmThreadLocalWatchpoint extends TeleWatchpoint {

        private final TeleThreadLocalValues teleThreadLocalValues;

        public TeleVmThreadLocalWatchpoint(WatchpointKind kind, Factory factory, String description, TeleThreadLocalValues teleThreadLocalValues, int index, WatchpointSettings settings) {
            super(kind, factory,  description, teleThreadLocalValues.getMemoryRegion(index), settings);
            this.teleThreadLocalValues = teleThreadLocalValues;
        }

        public boolean isRelocatable() {
            return false;
        }

        public TeleObject getTeleObject() {
            return null;
        }
    }

    /**
     * Abstraction for watchpoints covering some or all of an object, and which will
     * be relocated to follow the absolute location of the object whenever it is relocated by GC.
     */
    /**
     * @author Michael Van De Vanter
     *
     */
    private abstract static class TeleObjectWatchpoint extends TeleWatchpoint {

        /**
         * Watchpoint settings to use when a system watchpoint is placed on the field
         * to which a forwarding pointer gets written, designed to catch the relocation
         * of this specific object.
         */
        private static final WatchpointSettings relocationWatchpointSettings = new WatchpointSettings(false, true, false, true);

        /**
         * The VM heap object on which this watchpoint is set.
         */
        private TeleObject teleObject;

        /**
         * Starting location of the watchpoint, relative to the origin of the object.
         */
        private final Offset offset;

        /**
         * A hidden (system) watchpoint set on the object's field that the GC uses to store forwarding
         * pointers.
         */
        private TeleWatchpoint relocationWatchpoint = null;

        public TeleObjectWatchpoint(WatchpointKind kind, Factory factory, String description, TeleObject teleObject, Offset offset, Size size, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException  {
            super(kind, factory, description, teleObject.origin().plus(offset), size, settings);
            ProgramError.check(teleObject.isLive(), "Attempt to set an object-based watchpoint on an object that is not live: ", teleObject);
            this.teleObject = teleObject;
            this.offset = offset;
            setRelocationWatchpoint(teleObject.origin());
        }

        /**
         * Sets a watchpoint on the area of the object where GC writes a forwarding pointer; when
         * triggered, the watchpoint relocates this watchpoint as well as itself to the new location
         * identified by the forwarding pointer.
         *
         * @param origin the object origin for this watchpoint
         * @throws TooManyWatchpointsException
         */
        private void setRelocationWatchpoint(final Pointer origin) throws TooManyWatchpointsException {
            final TeleVM teleVM = factory.teleVM();
            final Pointer forwardPointerLocation = origin.plus(teleVM.gcForwardingPointerOffset());
            final MemoryRegion forwardPointerRegion = new FixedMemoryRegion(forwardPointerLocation, teleVM.wordSize(), "Forwarding pointer for object relocation watchpoint");
            relocationWatchpoint = factory.createSystemWatchpoint("Object relocation watchpoint", forwardPointerRegion, relocationWatchpointSettings);
            relocationWatchpoint.setTriggerEventHandler(new VMTriggerEventHandler() {

                public boolean handleTriggerEvent(TeleNativeThread teleNativeThread) {
                    final TeleObjectWatchpoint thisWatchpoint = TeleObjectWatchpoint.this;
                    if (teleVM.isObjectForwarded(origin)) {
                        final TeleObject newTeleObject = teleVM.getForwardedObject(origin);
                        if (newTeleObject == null) {
                            ProgramWarning.message("Unlable to find relocated teleObject" + this);
                        } else {
                            TeleObjectWatchpoint.this.teleObject = newTeleObject;
                            final Pointer newWatchpointStart = newTeleObject.origin().plus(thisWatchpoint.offset);
                            Trace.line(TRACE_VALUE, thisWatchpoint.tracePrefix() + " relocating watchpoint " + thisWatchpoint.start.toHexString() + "-->" + newWatchpointStart.toHexString());
                            thisWatchpoint.relocate(newWatchpointStart);
                            // Now replace this relocation watchpoint for the next time the objects gets moved.
                            thisWatchpoint.clearRelocationWatchpoint();
                            try {
                                thisWatchpoint.setRelocationWatchpoint(newTeleObject.origin());
                            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                                ProgramError.unexpected(thisWatchpoint.tracePrefix() + " failed to relocate the relocation watchpoint for " + thisWatchpoint);
                            }
                        }
                    } else {
                        Trace.line(TRACE_VALUE, thisWatchpoint.tracePrefix() + " relocating watchpoint (IGNORED) 0x" + thisWatchpoint.start.toHexString());
                    }

                    return false;
                }
            });
            relocationWatchpoint.setActive(true);
        }

        /**
         * Clears the watchpoint, if any, set on the area of the object where GC writes a forwarding pointer.
         */
        private void clearRelocationWatchpoint() {
            if (relocationWatchpoint != null) {
                relocationWatchpoint.dispose();
                relocationWatchpoint = null;
            }
        }

        @Override
        public boolean dispose() {
            clearRelocationWatchpoint();
            return super.dispose();
        }

        public final boolean isRelocatable() {
            return true;
        }

        public final TeleObject getTeleObject() {
            assert isAlive();
            return teleObject;
        }

        @Override
        protected void updateAfterGC() {
            assert isAlive();
            super.updateAfterGC();
            switch(teleObject.getTeleObjectMemoryState()) {
                case LIVE:
                    // A relocatable watchpoint on a live object should have been relocated
                    // (eagerly) just as the relocation took place.   Check that the locations match.
                    if (!teleObject.memoryRegion().start().plus(offset).equals(start())) {
                        ProgramWarning.message("Watchpoint relocation failure - watchpoint on live object at wrong location " + this);
                    }
                    break;
                case OBSOLETE:
                    // A relocatable watchpoint should not exist on an obsolete (forwarded)
                    // object.  It should not be permitted in the first place, and a transition
                    // from live to obsolete should have caused this watchpoint to be relocated.
                    ProgramWarning.message("Watchpoint relocation failure - watchpoint on obsolete object: " + this);
                    break;
                case DEAD:
                    // The watchpoint's object has been collected; convert it to a fixed memory region watchpoint
                    dispose();
                    final FixedMemoryRegion watchpointRegion = new FixedMemoryRegion(start(), size(), "Old memory location of watched object");
                    try {
                        final TeleWatchpoint newRegionWatchpoint =
                            factory.createRegionWatchpoint("Replacement for watchpoint on GC'd object", watchpointRegion, getSettings());
                        Trace.line(TRACE_VALUE, tracePrefix() + "Watchpoint on collected object replaced: " + newRegionWatchpoint);
                    } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                        ProgramWarning.message("Failed to replace object watchpoint with region watchpoint: " + tooManyWatchpointsException);
                    } catch (DuplicateWatchpointException duplicateWatchpointException) {
                        ProgramWarning.message("Failed to replace object watchpoint with region watchpoint: " + duplicateWatchpointException);
                    }
            }
        }
    }

    /**
     * A watchpoint for a whole object.
     */
    private static final class TeleWholeObjectWatchpoint extends TeleObjectWatchpoint {

        public TeleWholeObjectWatchpoint(WatchpointKind kind, Factory factory, String description, TeleObject teleObject, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            super(kind, factory, description, teleObject, Offset.zero(), teleObject.objectSize(), settings);
        }
    }

    /**
     * A watchpoint for the memory holding an object's field.
     */
    private static final class TeleFieldWatchpoint extends TeleObjectWatchpoint {

        public TeleFieldWatchpoint(WatchpointKind kind, Factory factory, String description, TeleObject teleObject, FieldActor fieldActor, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            super(kind, factory, description, teleObject, Offset.fromInt(fieldActor.offset()), teleObject.fieldSize(fieldActor), settings);
        }
    }

    /**
     *A watchpoint for the memory holding an array element.
     */
    private static final class TeleArrayElementWatchpoint extends TeleObjectWatchpoint {

        public TeleArrayElementWatchpoint(WatchpointKind kind, Factory factory, String description, TeleObject teleObject, Kind elementKind, Offset arrayOffsetFromOrigin, int index, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            super(kind, factory, description, teleObject, arrayOffsetFromOrigin.plus(index * elementKind.width.numberOfBytes), Size.fromInt(elementKind.width.numberOfBytes), settings);
        }
    }

    /**
     * A watchpoint for the memory holding an object's header field.
     */
    private static final class TeleHeaderWatchpoint extends TeleObjectWatchpoint {

        public TeleHeaderWatchpoint(WatchpointKind kind, Factory factory, String description, TeleObject teleObject, HeaderField headerField, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            super(kind, factory, description, teleObject, teleObject.headerOffset(headerField), teleObject.headerSize(headerField), settings);
        }
    }

    /**
     * A factory for creating and managing process watchpoints.
     * <br>
     * Overlapping watchpoints are not permitted.
     *
     * @author Michael Van De Vanter
     */
    public static class Factory extends AbstractTeleVMHolder {

        private final TeleProcess teleProcess;

        private final Comparator<TeleWatchpoint> watchpointComparator = new Comparator<TeleWatchpoint>() {

            public int compare(TeleWatchpoint o1, TeleWatchpoint o2) {
                // For the purposes of the collection, define equality and comparison to be based
                // exclusively on starting address.
                return o1.start().compareTo(o2.start());
            }
        };

        // This implementation is not thread-safe; this factory must take care of that.
        // Keep the set ordered by start address only, implemented by the comparator and equals().
        // An additional constraint imposed by this factory is that no regions overlap,
        // either in part or whole, with others in the set.
        private final TreeSet<TeleWatchpoint> clientWatchpoints = new TreeSet<TeleWatchpoint>(watchpointComparator);

        // A thread-safe, immutable collection of the current watchpoint list.
        // This list will be read many, many more times than it will change.
        private volatile IterableWithLength<MaxWatchpoint> clientWatchpointsCache = Sequence.Static.empty(MaxWatchpoint.class);

        // Watchpoints used for internal purposes, for example for GC and relocation services
        private final TreeSet<TeleWatchpoint> systemWatchpoints = new TreeSet<TeleWatchpoint>(watchpointComparator);
        private volatile IterableWithLength<MaxWatchpoint> systemWatchpointsCache = Sequence.Static.empty(MaxWatchpoint.class);

        /**
         * A listener for GC completions, whenever there are any watchpoints; null when no watchpoints.
         */
        private MaxGCCompletedListener gcCompletedListener = null;

        private List<MaxWatchpointListener> watchpointListeners = new CopyOnWriteArrayList<MaxWatchpointListener>();

        /**
         * Creates a factory for creating and managing watchpoints in the vm.
         *
         */
        public Factory(TeleVM teleVM, TeleProcess teleProcess) {
            super(teleVM);
            this.teleProcess = teleProcess;
            teleVM().addVMStateListener(new MaxVMStateListener() {

                public void stateChanged(MaxVMState maxVMState) {
                    if (maxVMState.processState() == ProcessState.TERMINATED) {
                        clientWatchpoints.clear();
                        systemWatchpoints.clear();
                        updateAfterWatchpointChanges();
                    }
                }
            });
        }

        /**
         * Adds a listener for watchpoint changes.
         *
         * @param listener a watchpoint listener
         */
        public final void addWatchpointListener(MaxWatchpointListener listener) {
            assert listener != null;
            watchpointListeners.add(listener);
        }

        /**
         * Removes a listener for watchpoint changes.
         *
         * @param listener a watchpoint listener
         */
        public final void removeWatchpointListener(MaxWatchpointListener listener) {
            assert listener != null;
            watchpointListeners.remove(listener);
        }

        /**
         * Creates a new, active watchpoint that covers a given memory region in the VM.
         * <br>
         * The trigger occurs <strong>after</strong> the specified event.
         *
         * @param description text useful to a person, for example capturing the intent of the watchpoint
         * @param memoryRegion the region of memory in the VM to be watched.
         * @param settings initial settings for the watchpoint
         *
         * @return a new watchpoint, if successful
         * @throws TooManyWatchpointsException if setting a watchpoint would exceed a platform-specific limit
         * @throws DuplicateWatchpointException if the region overlaps, in part or whole, with an existing watchpoint.
         */
        public synchronized TeleWatchpoint createRegionWatchpoint(String description, MemoryRegion memoryRegion, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            final TeleWatchpoint teleWatchpoint = new TeleRegionWatchpoint(WatchpointKind.CLIENT, this, description, memoryRegion, settings);
            return addClientWatchpoint(teleWatchpoint);
        }

        /**
         * Creates a new, active watchpoint that covers an entire heap object's memory in the VM.
         * @param description text useful to a person, for example capturing the intent of the watchpoint
         * @param teleObject a heap object in the VM
         * @param settings initial settings for the watchpoint
         * @return a new watchpoint, if successful
         * @throws TooManyWatchpointsException if setting a watchpoint would exceed a platform-specific limit
         * @throws DuplicateWatchpointException if the region overlaps, in part or whole, with an existing watchpoint.
         */
        public synchronized TeleWatchpoint createObjectWatchpoint(String description, TeleObject teleObject, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            final TeleWatchpoint teleWatchpoint = new TeleWholeObjectWatchpoint(WatchpointKind.CLIENT, this, description, teleObject, settings);
            return addClientWatchpoint(teleWatchpoint);
        }

        /**
         * Creates a new, active watchpoint that covers a heap object's field in the VM.
         * @param description text useful to a person, for example capturing the intent of the watchpoint
         * @param teleObject a heap object in the VM
         * @param fieldActor description of a field in object of that type
         * @param settings initial settings for the watchpoint
         * @return a new watchpoint, if successful
         * @throws TooManyWatchpointsException if setting a watchpoint would exceed a platform-specific limit
         * @throws DuplicateWatchpointException if the region overlaps, in part or whole, with an existing watchpoint.
         */
        public synchronized TeleWatchpoint createFieldWatchpoint(String description, TeleObject teleObject, FieldActor fieldActor, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            final TeleWatchpoint teleWatchpoint = new TeleFieldWatchpoint(WatchpointKind.CLIENT, this, description, teleObject, fieldActor, settings);
            return addClientWatchpoint(teleWatchpoint);
        }

        /**
         * Creates a new, active watchpoint that covers an element in an array in the VM.
         *
         * @param description text useful to a person, for example capturing the intent of the watchpoint
         * @param teleObject a heap object in the VM that contains the array
         * @param elementKind the type category of the array elements
         * @param arrayOffsetFromOrigin location relative to the object's origin of element 0 in the array
         * @param index index of the element to watch
         * @param settings initial settings for the watchpoint
         * @return a new watchpoint, if successful
         * @throws TooManyWatchpointsException if setting a watchpoint would exceed a platform-specific limit
         * @throws DuplicateWatchpointException if the region overlaps, in part or whole, with an existing watchpoint.
         */
        public synchronized TeleWatchpoint createArrayElementWatchpoint(String description, TeleObject teleObject, Kind elementKind, Offset arrayOffsetFromOrigin, int index, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            final TeleWatchpoint teleWatchpoint = new TeleArrayElementWatchpoint(WatchpointKind.CLIENT, this, description, teleObject, elementKind, arrayOffsetFromOrigin, index, settings);
            return addClientWatchpoint(teleWatchpoint);
        }

        /**
         * Creates a new, active watchpoint that covers a field in an object's header in the VM.
         *
         * @param description text useful to a person, for example capturing the intent of the watchpoint
         * @param teleObject a heap object in the VM
         * @param headerField a field in the object's header
         * @param settings initial settings for the watchpoint
         * @return a new watchpoint, if successful
         * @throws TooManyWatchpointsException if setting a watchpoint would exceed a platform-specific limit
         * @throws DuplicateWatchpointException if the region overlaps, in part or whole, with an existing watchpoint.
         */
        public synchronized TeleWatchpoint createHeaderWatchpoint(String description, TeleObject teleObject, HeaderField headerField, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            final TeleWatchpoint teleWatchpoint = new TeleHeaderWatchpoint(WatchpointKind.CLIENT, this, description, teleObject, headerField, settings);
            return addClientWatchpoint(teleWatchpoint);
        }

        /**
         * Creates a new, active watchpoint that covers a thread local variable in the VM.
         *
         * @param description text useful to a person, for example capturing the intent of the watchpoint
         * @param teleThreadLocalValues a set of thread local values
         * @param index identifies the particular thread local variable
         * @param settings initial settings for the watchpoint
         * @return a new watchpoint, if successful
         * @throws TooManyWatchpointsException if setting a watchpoint would exceed a platform-specific limit
         * @throws DuplicateWatchpointException if the region overlaps, in part or whole, with an existing watchpoint.
         */
        public synchronized TeleWatchpoint createVmThreadLocalWatchpoint(String description, TeleThreadLocalValues teleThreadLocalValues, int index, WatchpointSettings settings)
            throws TooManyWatchpointsException, DuplicateWatchpointException {
            final TeleWatchpoint teleWatchpoint = new TeleVmThreadLocalWatchpoint(WatchpointKind.CLIENT, this, description, teleThreadLocalValues, index, settings);
            return addClientWatchpoint(teleWatchpoint);
        }

        /**
         * Find an existing client watchpoint set in the VM.
         *
         * @param address a memory address in the VM
         * @return the watchpoint whose memory region includes the address, null if none.
         */
        synchronized TeleWatchpoint findClientWatchpoint(Address address) {
            for (TeleWatchpoint teleWatchpoint : clientWatchpoints) {
                if (teleWatchpoint.contains(address)) {
                    return teleWatchpoint;
                }
            }
            return null;
        }

        /**
         * Find existing client memory watchpoints in the VM by location.
         * <br>
         * thread-safe
         *
         * @param memoryRegion a memory region in the VM
         * @return all watchpoints whose memory regions overlap the specified region, empty sequence if none.
         */
        public Sequence<MaxWatchpoint> findClientWatchpoints(MemoryRegion memoryRegion) {
            DeterministicSet<MaxWatchpoint> watchpoints = DeterministicSet.Static.empty(MaxWatchpoint.class);
            for (MaxWatchpoint maxWatchpoint : clientWatchpointsCache) {
                if (maxWatchpoint.overlaps(memoryRegion)) {
                    if (watchpoints.isEmpty()) {
                        watchpoints = new DeterministicSet.Singleton<MaxWatchpoint>(maxWatchpoint);
                    } else if (watchpoints.length() == 1) {
                        GrowableDeterministicSet<MaxWatchpoint> newSet = new LinkedIdentityHashSet<MaxWatchpoint>(watchpoints.first());
                        newSet.add(maxWatchpoint);
                        watchpoints = newSet;
                    } else {
                        final GrowableDeterministicSet<MaxWatchpoint> growableSet = (GrowableDeterministicSet<MaxWatchpoint>) watchpoints;
                        growableSet.add(maxWatchpoint);
                    }
                }
            }
            return watchpoints;
        }

        /**
         * @return all watchpoints currently set in the VM; thread-safe.
         */
        public IterableWithLength<MaxWatchpoint> clientWatchpoints() {
            // Hand out the cached, thread-safe summary
            return clientWatchpointsCache;
        }

        /**
         * Creates a new, inactive system watchpoint. This watchpoint is not shown in the list of current watchpoints.
         * This watchpoint has to be explicitly activated.
         *
         * @param description a human-readable description of the watchpoint's purpose, for debugging.
         * @param memoryRegion the memory region to watch.
         * @param settings initial settings for the watchpoint
         * @return a new, inactive system watchpoint
         * .
         * @throws TooManyWatchpointsException
         */
        private synchronized TeleWatchpoint createSystemWatchpoint(String description, MemoryRegion memoryRegion, WatchpointSettings settings) throws TooManyWatchpointsException {
            final TeleWatchpoint teleWatchpoint = new TeleRegionWatchpoint(WatchpointKind.SYSTEM, this, description, memoryRegion, settings);
            return addSystemWatchpoint(teleWatchpoint);
        }

        /**
         * Find an system watchpoint set at a particular location.
         *
         * @param address a location in VM memory
         * @return a system watchpoint, null if none exists at the address.
         */
        public TeleWatchpoint findSystemWatchpoint(Address address) {
            for (TeleWatchpoint teleWatchpoint : systemWatchpoints) {
                if (teleWatchpoint.contains(address)) {
                    return teleWatchpoint;
                }
            }
            return null;
        }

        /**
         * Updates the watchpoint caches of memory contents.
         */
        void updateWatchpointMemoryCaches() {
            for (TeleWatchpoint teleWatchpoint : clientWatchpoints) {
                teleWatchpoint.updateMemoryCache();
            }
        }

        /**
         * @return total number of existing watchpoints of all kinds.[
         */
        private int watchpointCount() {
            return clientWatchpoints.size() + systemWatchpoints.size();
        }

        private void updateAfterWatchpointChanges() {
            clientWatchpointsCache = new VectorSequence<MaxWatchpoint>(clientWatchpoints);
            systemWatchpointsCache = new VectorSequence<MaxWatchpoint>(systemWatchpoints);
            // Ensure that the factory listens for GC completion events iff
            // there are watchpoints.
            if (watchpointCount() > 0) {
                if (gcCompletedListener == null) {
                    gcCompletedListener = new MaxGCCompletedListener() {

                        public void gcCompleted() {

                            for (MaxWatchpoint maxWatchpoint : clientWatchpointsCache) {
                                final TeleWatchpoint teleWatchpoint = (TeleWatchpoint) maxWatchpoint;
                                Trace.line(TRACE_VALUE, teleWatchpoint.tracePrefix() + "updating after GC: " + teleWatchpoint);
                                teleWatchpoint.updateAfterGC();
                            }
                        }
                    };
                    teleVM().addGCCompletedListener(gcCompletedListener);
                }
            } else { // no watchpoints
                if (gcCompletedListener != null) {
                    teleVM().removeGCCompletedListener(gcCompletedListener);
                    gcCompletedListener = null;
                }
            }
            for (final MaxWatchpointListener listener : watchpointListeners) {
                listener.watchpointsChanged();
            }
        }

        /**
         * Adds a watchpoint to the list of current client watchpoints, and activates this watchpoint.
         * <br>
         * If the addition fails, the watchpoint is not activated.
         *
         * @param teleWatchpoint the new client watchpoint, presumed to be inactive and not to have been added before.
         * @return the watchpoint, null if failed to create for some reason
         * @throws TooManyWatchpointsException
         * @throws DuplicateWatchpointException
         */
        private TeleWatchpoint addClientWatchpoint(TeleWatchpoint teleWatchpoint)  throws TooManyWatchpointsException, DuplicateWatchpointException {
            assert teleWatchpoint.kind == WatchpointKind.CLIENT;
            assert teleWatchpoint.isAlive();
            assert !teleWatchpoint.isActive();

            if (watchpointCount() >= teleProcess.maximumWatchpointCount()) {
                throw new TooManyWatchpointsException("Number of watchpoints supported by platform (" +
                    teleProcess.maximumWatchpointCount() + ") exceeded");
            }
            if (!clientWatchpoints.add(teleWatchpoint)) {
                // TODO (mlvdv) call out special case where there's a hidden system watchpoint at the same location as this.
                // An existing watchpoint starts at the same location
                throw new DuplicateWatchpointException("Watchpoint already exists at location: " + teleWatchpoint);
            }
            // Check for possible overlaps with predecessor or successor (according to start location)
            final TeleWatchpoint lowerWatchpoint = clientWatchpoints.lower(teleWatchpoint);
            final TeleWatchpoint higherWatchpoint = clientWatchpoints.higher(teleWatchpoint);
            if ((lowerWatchpoint != null && lowerWatchpoint.overlaps(teleWatchpoint)) ||
                            (higherWatchpoint != null && higherWatchpoint.overlaps(teleWatchpoint))) {
                clientWatchpoints.remove(teleWatchpoint);
                throw new DuplicateWatchpointException("Watchpoint already exists that overlaps with start=" + teleWatchpoint.start().toHexString() + ", size=" + teleWatchpoint.size().toString());
            }
            if (!teleVM().isInGC() || teleWatchpoint.settings.enabledDuringGC) {
                // Try to activate the new watchpoint
                if (!teleWatchpoint.setActive(true)) {
                    clientWatchpoints.remove(teleWatchpoint);
                    return null;
                }
            }
            Trace.line(TRACE_VALUE, teleWatchpoint.tracePrefix() + "added watchpoint: " + teleWatchpoint);
            updateAfterWatchpointChanges();
            return teleWatchpoint;
        }

        /**
         * Add a system watchpoint, assumed to be newly created.
         * <br>Does <strong>not</strong> activate the watchpoint.
         * <br>Does <strong>not</strong> check for overlap with existing watchpoints.
         *
         * @param teleWatchpoint
         * @return the watchpoint
         * @throws TooManyWatchpointsException
         */
        private TeleWatchpoint addSystemWatchpoint(TeleWatchpoint teleWatchpoint) throws TooManyWatchpointsException {
            if (watchpointCount() >= teleProcess.maximumWatchpointCount()) {
                throw new TooManyWatchpointsException("Number of watchpoints supported by platform (" +
                    teleProcess.maximumWatchpointCount() + ") exceeded");
            }
            systemWatchpoints.add(teleWatchpoint);
            updateAfterWatchpointChanges();
            return teleWatchpoint;
        }

        /**
         * Removes a memory watchpoint from the VM.
         * <br>
         * Notifies observers if a client watchpoint.
         *
         * @param teleWatchpoint an existing, inactive watchpoint in the VM
         * @return true if successful
         */
        private synchronized boolean removeWatchpoint(TeleWatchpoint teleWatchpoint) {
            assert teleWatchpoint.isAlive();
            assert !teleWatchpoint.isActive();

            switch(teleWatchpoint.kind) {
                case CLIENT: {
                    if (!clientWatchpoints.remove(teleWatchpoint)) {
                        ProgramError.unexpected(teleWatchpoint.tracePrefix + " Failed to remove watchpoint: " + teleWatchpoint);
                    }
                    Trace.line(TRACE_VALUE, teleWatchpoint.tracePrefix + "Removed watchpoint: " + teleWatchpoint);
                    updateAfterWatchpointChanges();
                    return true;
                }
                case SYSTEM: {
                    if (!systemWatchpoints.remove(teleWatchpoint)) {
                        ProgramError.unexpected(teleWatchpoint.tracePrefix + " Failed to remove watchpoint: " + teleWatchpoint);
                    }
                    Trace.line(TRACE_VALUE, teleWatchpoint.tracePrefix + "Removed watchpoint: " + teleWatchpoint);
                    return true;
                }
                default:
                    ProgramError.unknownCase();
                    return false;
            }
        }

        public void writeSummaryToStream(PrintStream printStream) {
            printStream.println("Watchpoints :");
            for (TeleWatchpoint teleWatchpoint : clientWatchpoints) {
                printStream.println("  " + teleWatchpoint.toString());

            }
            for (TeleWatchpoint teleWatchpoint : systemWatchpoints) {
                printStream.println("  " + teleWatchpoint.toString());
            }
        }
    }

    public static class TooManyWatchpointsException extends MaxException {
        TooManyWatchpointsException(String message) {
            super(message);
        }
    }

    public static class DuplicateWatchpointException extends MaxException {
        DuplicateWatchpointException(String message) {
            super(message);
        }
    }

}
