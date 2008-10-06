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
/*VCSID=8554c495-2ecd-4c46-85e1-ab3826b6d77b*/
package com.sun.max.ins.debug;

import com.sun.max.ins.*;
import com.sun.max.ins.value.*;
import com.sun.max.tele.debug.*;
import com.sun.max.util.*;

/**
 * @author Bernd Mathiske
 */
public final class StateRegisterPanel extends RegisterPanel {

    public StateRegisterPanel(Inspection inspection, TeleStateRegisters registers) {
        super(inspection, registers);
    }

    @Override
    public TeleStateRegisters registers() {
        return (TeleStateRegisters) super.registers();
    }

    @Override
    protected WordValueLabel.ValueMode registerLabelValueMode(Symbol register) {
        if (registers().isFlagsRegister(register)) {
            return WordValueLabel.ValueMode.FLAGS_REGISTER;
        }
        if (registers().isInstructionPointerRegister(register)) {
            return WordValueLabel.ValueMode.CALL_ENTRY_POINT;
        }
        return WordValueLabel.ValueMode.INTEGER_REGISTER;
    }
}
