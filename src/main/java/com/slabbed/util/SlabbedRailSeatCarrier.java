package com.slabbed.util;

/**
 * The minecart's bound rail seat, shared between the entity mixin (which stores and binds it) and the
 * movement-controller mixin (which keeps rail coordinates logical while the cart sits physically on
 * the drawn slope). From 1.21.2 rail movement lives on the minecart controller, so the two halves of
 * what was one mixin talk through this.
 */
public interface SlabbedRailSeatCarrier {
    double slabbed$railDy();

    boolean slabbed$inLogicalRailQuery();

    void slabbed$enterLogicalRailQuery();

    void slabbed$exitLogicalRailQuery();

    /**
     * Binds the seat of the rail cell the LOGICAL position belongs to (off any rail, the bound seat
     * stays) and writes the physical position with it; {@code top} binds the seat that puts the cart
     * at the top of the rail's drawn profile (the height the collision sweep runs at).
     */
    void slabbed$bindAndPlace(double x, double logicalY, double z, boolean top);
}
