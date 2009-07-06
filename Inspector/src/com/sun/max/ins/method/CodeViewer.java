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
package com.sun.max.ins.method;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import com.sun.max.collect.*;
import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.method.*;
import com.sun.max.vm.stack.*;

/**
 * Base class for panels that show a row-oriented view of a method in a MethodInspector framework.
 * Not intended for use outside a MethodInspector, so not undockable;
 * Includes machinery for some common operations, based on abstract "rows"
 * - maintaining a cache that maps row->stackFrame for the thread of current focus
 * - tracking which rows are "active", i.e. have some frame at that location for the thread of current focus
 * - an action, attached to a toolbar button, that scrolls to the next active row
 * - a "search" function that causes a separate toolbar to appear that permits regexp row-based searching.
 *
 * @author Michael Van De Vanter
 */
public abstract class CodeViewer extends InspectorPanel {

    private static final int TRACE_VALUE = 2;

    private final MethodInspector parent;

    private JPanel toolBarPanel;
    private JToolBar toolBar;
    private RowTextSearchToolBar searchToolBar;
    private final JButton searchButton;
    private final JButton activeRowsButton;
    private JButton viewCloseButton;

    public MethodInspector parent() {
        return parent;
    }

    protected JToolBar toolBar() {
        return toolBar;
    }

    public abstract MethodCodeKind codeKind();

    public abstract String codeViewerKindName();

    public abstract void print(String name);

    public abstract boolean updateCodeFocus(TeleCodeLocation teleCodeLocation);

    public void updateThreadFocus(MaxThread thread) {
        updateCaches(false);
    }

    public CodeViewer(Inspection inspection, MethodInspector parent) {
        super(inspection, new BorderLayout());
        this.parent = parent;

        searchButton = new InspectorButton(inspection, new AbstractAction("Search...") {
            public void actionPerformed(ActionEvent actionEvent) {
                addSearchToolBar();
            }
        });
        searchButton.setToolTipText("Open toolbar for searching");

        activeRowsButton = new InspectorButton(inspection, new AbstractAction(null, style().debugActiveRowButtonIcon()) {
            public void actionPerformed(ActionEvent actionEvent) {
                int nextActiveRow = nextActiveRow();
                if (nextActiveRow >= 0) {
                    if (nextActiveRow == getSelectedRow()) {
                        // If already at an active row, go to the next one, if it exists.
                        nextActiveRow = nextActiveRow();
                    }
                    setFocusAtRow(nextActiveRow);
                }
            }
        });
        activeRowsButton.setForeground(style().debugIPTagColor());
        activeRowsButton.setToolTipText("Scroll to next line with IP or Call Return");
        activeRowsButton.setEnabled(false);

        viewCloseButton =
            new InspectorButton(inspection(), "", "Close " + codeViewerKindName());
        viewCloseButton.setAction(new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                parent().closeCodeViewer(CodeViewer.this);
            }
        });
        viewCloseButton.setIcon(style().codeViewCloseIcon());

        //getActionMap().put(SEARCH_ACTION, new SearchAction());

        // TODO (mlvdv)  generalize so that this binding comes from a preference
        //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke('F', CTRL_DOWN_MASK), SEARCH_ACTION);
    }

    protected void createView() {
        toolBarPanel = new InspectorPanel(inspection(), new GridLayout(0, 1));
        toolBar = new InspectorToolBar(inspection());
        toolBar.setFloatable(false);
        toolBar.setRollover(true);
        toolBarPanel.add(toolBar);
        add(toolBarPanel, BorderLayout.NORTH);
    }

    private IndexedSequence<Integer> searchMatchingRows = null;

    /**
     * @return the rows that match a current search session; null if no search session active.
     */
    protected final IndexedSequence<Integer> getSearchMatchingRows() {
        return searchMatchingRows;
    }

    private final RowSearchListener searchListener = new RowSearchListener() {

        public void searchResult(IndexedSequence<Integer> result) {
            searchMatchingRows = result;
            // go to next matching row from current selection
            if (searchMatchingRows != null) {
                Trace.line(TRACE_VALUE, "search: matches " + searchMatchingRows.length() + " = " + searchMatchingRows);
            }
            repaint();
        }

        public void selectNextResult() {
            setFocusAtNextSearchMatch();
        }

        public void selectPreviousResult() {
            setFocusAtPreviousSearchMatch();
        }

        public void closeSearch() {
            CodeViewer.this.closeSearch();
        }
    };

    private void addSearchToolBar() {
        if (searchToolBar == null) {
            searchToolBar = new RowTextSearchToolBar(inspection(), searchListener, getRowTextSearcher());
            toolBarPanel.add(searchToolBar);
            parent().frame().pack();
            searchToolBar.getFocus();
        }
    }

    private void closeSearch() {
        Trace.line(TRACE_VALUE, "search:  closing");
        toolBarPanel.remove(searchToolBar);
        parent().frame().pack();
        searchToolBar = null;
        searchMatchingRows = null;
    }

    private void setFocusAtNextSearchMatch() {
        Trace.line(TRACE_VALUE, "search:  next match");
        if (searchMatchingRows.length() > 0) {
            int currentRow = getSelectedRow();
            for (int row : searchMatchingRows) {
                if (row > currentRow) {
                    setFocusAtRow(row);
                    return;
                }
            }
            // wrap, could be optional, or dialog choice
            currentRow = -1;
            for (int row : searchMatchingRows) {
                if (row > currentRow) {
                    setFocusAtRow(row);
                    return;
                }
            }
        } else {
            flash();
        }
    }

    private void setFocusAtPreviousSearchMatch() {
        Trace.line(TRACE_VALUE, "search:  previous match");
        if (searchMatchingRows.length() > 0) {
            int currentRow = getSelectedRow();
            for (int index = searchMatchingRows.length() - 1; index >= 0; index--) {
                final Integer matchingRow = searchMatchingRows.get(index);
                if (matchingRow < currentRow) {
                    setFocusAtRow(matchingRow);
                    return;
                }
            }
            // wrap, could be optional, or dialog choice
            currentRow = getRowCount();
            for (int index = searchMatchingRows.length() - 1; index >= 0; index--) {
                final Integer matchingRow = searchMatchingRows.get(index);
                if (matchingRow < currentRow) {
                    setFocusAtRow(matchingRow);
                    return;
                }
            }
        } else {
            flash();
        }
    }


    /**
     * @return a searcher for locating rows with a textual regexp.
     */
    protected abstract RowTextSearcher getRowTextSearcher();

    /**
     * @return how man rows are in the view.
     */
    protected abstract int getRowCount();

    /**
     * @return the row in a code display that is currently selected (at code focus); -1 if no selection
     */
    protected abstract int getSelectedRow();

    /**
     * Sets the global focus of code location at the code being displayed in the row.
     */
    protected abstract void setFocusAtRow(int row);

    /**
     * Adds a button to the view's tool bar that enables textual search.
     */
    protected void addSearchButton() {
        toolBar().add(searchButton);
    }

    /**
     * Adds a button to the view's tool bar that enables navigation among "active" rows, those that correspond to
     * stack locations in the current thread.
     */
    protected void addActiveRowsButton() {
        toolBar().add(activeRowsButton);
    }

    /**
     * Adds a button to the view's tool bar that closes this view.
     */
    protected void addCodeViewCloseButton() {
        toolBar.add(viewCloseButton);
    }

    @Override
    public final void refresh(boolean force) {
        updateCaches(force);
        updateView(force);
        updateSize();
        invalidate();
        repaint();
    }

    protected void updateSize() {
        for (int index = 0; index < getComponentCount(); index++) {
            final Component component = getComponent(index);
            if (component instanceof JScrollPane) {
                final JScrollPane scrollPane = (JScrollPane) component;
                final Dimension size = scrollPane.getViewport().getPreferredSize();
                setMaximumSize(new Dimension(size.width + 40, size.height + 40));
            }
        }
    }

    protected void flash() {
        parent.frame().flash(style().frameBorderFlashColor());
    }


    /**
     * Summary information for a frame on the stack.
     */
    protected final class StackFrameInfo {

        private final StackFrame stackFrame;

        /**
         * @return the {@link StackFrame}
         */
        public StackFrame frame() {
            return stackFrame;
        }

        private final MaxThread thread;

        /**
         * @return the thread in whose stack the frame resides.
         */
        public MaxThread thread() {
            return thread;
        }

        private final int stackPosition;

        /**
         * @return the position of the frame on the stack, with 0 at top
         */
        public int position() {
            return stackPosition;
        }

        public StackFrameInfo(StackFrame stackFrame, MaxThread thread, int stackPosition) {
            this.stackFrame = stackFrame;
            this.thread = thread;
            this.stackPosition = stackPosition;
        }
    }

    // Cached stack information, relative to this method, derived from the thread of current focus.
    // TODO (mlvdv) Generalize to account for the possibility of multiple stack frames associated with a single row.
    protected StackFrameInfo[] rowToStackFrameInfo;

    /**
     * Rebuild the data in the cached stack information for the code view.
     */
    protected abstract void updateStackCache();

    // The thread from which the stack cache was last built.
    private MaxThread threadForCache = null;

    private MaxVMState lastRefreshedState = null;

    private void updateCaches(boolean force) {
        final MaxThread thread = inspection().focus().thread();
        if (thread != threadForCache || maxVMState().newerThan(lastRefreshedState) || force) {
            lastRefreshedState = maxVMState();
            updateStackCache();
            // Active rows depend on the stack cache.
            updateActiveRows();
            threadForCache = thread;
        }
    }

    /**
     * Updates any label in the view that are based on state in the VM.
     *
     */
    protected abstract void updateView(boolean force);

    /**
     * Returns stack frame information, if any, associated with the row.
     */
    protected StackFrameInfo stackFrameInfo(int row) {
        return rowToStackFrameInfo[row];
    }

    /**
     * Is the target code address at the row an instruction pointer
     * for a non-top frame of the stack of the thread that is the current focus?
     */
    protected boolean isCallReturn(int row) {
        final StackFrameInfo stackFrameInfo = rowToStackFrameInfo[row];
        return stackFrameInfo != null && !stackFrameInfo.frame().isTopFrame();
    }

    /**
     * Is the target code address at the row an instruction pointer
     * for the top frame of the stack of the thread that is the current focus?
     */
    protected boolean isInstructionPointer(int row) {
        final StackFrameInfo stackFrameInfo = rowToStackFrameInfo[row];
        return stackFrameInfo != null && stackFrameInfo.frame().isTopFrame();
    }

    // Active rows are those for which there is an associated stack frame
    private VectorSequence<Integer> activeRows = new VectorSequence<Integer>(3);
    private int currentActiveRowIndex = -1;

    private void updateActiveRows() {
        activeRows.clear();
        for (int row = 0; row < rowToStackFrameInfo.length; row++) {
            if (rowToStackFrameInfo[row] != null) {
                activeRows.append(row);
            }
        }
        currentActiveRowIndex = -1;
        activeRowsButton.setEnabled(hasActiveRows());
    }

    /**
     * Does the method have any rows that are either the current instruction pointer or call return lines marked.
     */
    protected boolean hasActiveRows() {
        return activeRows.length() > 0;
    }

    /**
     * Cycles through the rows in the method that are either the current instruction pointer or call return lines marked.
     * Resets to the first after each refresh.
     */
    protected int nextActiveRow() {
        if (hasActiveRows()) {
            currentActiveRowIndex = (currentActiveRowIndex + 1) % activeRows.length();
            return activeRows.elementAt(currentActiveRowIndex);
        }
        return -1;
    }

    // TODO (mlvdv) figure out how to make this a view-specific binding without interference with global menu item accelerators.
//    private final class SearchAction extends InspectorAction {
//
//        SearchAction() {
//            super(inspection(), SEARCH_ACTION);
//        }
//
//        @Override
//        public void procedure() {
//            addSearchToolBar();
//        }
//    }

}
