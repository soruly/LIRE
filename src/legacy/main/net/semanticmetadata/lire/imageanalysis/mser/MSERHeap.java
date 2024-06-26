/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval -
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * --------------------
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *     http://www.semanticmetadata.net/lire, http://www.lire-project.net
 */

package net.semanticmetadata.lire.imageanalysis.mser;

import java.util.Stack;

/**
 * Implements a HEAP for the Image pixels.
 * <p/>
 * User: Shotty
 * Date: 28.06.2010
 * Time: 23:43:50
 */
public class MSERHeap {
    // bitmask
    boolean[] bitmask;
    // stack of available pixels for each level
    Stack<BoundaryPixel>[] stacks;

    // normally MSER has 256 levels (0-255)
    public MSERHeap(int levels) {
        bitmask = new boolean[levels];
        stacks = new Stack[levels];
        for (int i = 0; i < levels; i++) {
            // no pixels available at the beginning
            bitmask[i] = false;
            // every level has its own stack of pixels
            stacks[i] = new Stack<BoundaryPixel>();
        }
    }

    /**
     * Pop the next pixel of the smallest level.
     * Returns null if the heap is completely empty...
     *
     * @return the next BoundaryPixel
     */
    public BoundaryPixel pop() {
        for (int i = 0; i < bitmask.length; i++) {
            // if there are pixels in the level
            if (bitmask[i]) {
                // pop the BoundaryPixel
                BoundaryPixel pix = stacks[i].pop();
                // update if stack for this level is now empty
                bitmask[i] = !stacks[i].empty();
                return pix;
            }
        }
        // no pixel in the heap
        return null;
    }

    /**
     * #
     * Pushes a pixel in the heap of the given grey value
     *
     * @param e         the pixel
     * @param greyValue the maxGreyValue (level) of the pixel
     */
    public void push(BoundaryPixel e, int greyValue) {
        // update if stack on this level is not empty anymore
        if (!bitmask[greyValue]) {
            bitmask[greyValue] = true;
        }
        // put the pixel on the right stack
        stacks[greyValue].push(e);
    }
}
