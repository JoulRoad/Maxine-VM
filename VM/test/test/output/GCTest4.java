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
package test.output;

public class GCTest4 {

    public static void main(String[] args) {
        for (int i = 0; i < 50; i++) {
            System.out.println("Iteration " + i + "...");
            createList();
        }
        System.out.println(GCTest4.class.getSimpleName() + " done.");
    }

    private static void createList() {
        // build a list of length 1000
        final Node1 start = new Node1("0");
        Node1 previous = start;
        final int length = 10000;
        for (int i = 1; i < length; i++) {
            final Node1 temp = new Node1(String.valueOf(i));
            previous.setNext(temp);
            previous = temp;
        }

        // verify the contents of the list
        int len = 0;
        Node1 node = start;
        while (node != null) {
            if (!node.id.equals(String.valueOf(len))) {
                throw new Error("assert fail");
            }
            node = node.next;
            len++;
        }
        if (len != length) {
            throw new Error("assert fail");
        }
    }

    public static void printList(Node1 start) {
        Node1 temp = start;
        while (temp.getNext() != null) {
            System.out.print(temp.getId() + ", ");
            temp = temp.getNext();
        }
    }

}

class Node1 {

    String id;
    Node1 next = null;
    long[] array;

    Node1(String id) {
        this.id = id;
        this.array = new long[500];
    }

    public String getId() {
        return id;
    }

    public void setNext(Node1 next) {
        this.next = next;
    }

    public Node1 getNext() {
        return next;
    }

}
