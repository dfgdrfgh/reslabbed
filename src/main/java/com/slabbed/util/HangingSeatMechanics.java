package com.slabbed.util;

/**
 * Duck interface on hung decorations for the seat mechanics the decoration base class implements and
 * a subclass may have to drive itself (1.21.5: the item frame overrides its facing setter and its
 * survival check without chaining to the base class):
 * <ul>
 *   <li>{@link #slabbed$mintSeatIfMissing()} mints the remembered seat from the supporting block once
 *       the facing is known — on the server thread, never waiting on a chunk — and returns whether it did;</li>
 *   <li>the survival pair moves the bounding box back onto its grid cell for the duration of vanilla's
 *       attachment-survival check and restores it afterwards.</li>
 * </ul>
 */
public interface HangingSeatMechanics {
    boolean slabbed$mintSeatIfMissing();

    void slabbed$beginSurvivalOnGrid();

    void slabbed$endSurvivalOnGrid();
}
