package com.slabbed.client;

/**
 * A render-only height carried on an entity render state from {@code updateRenderState} (where the
 * entity and its world are at hand) to {@code getPositionOffset} (where only the state is). Legacy
 * cells without a stored seat draw their hung frame or cart lowered by this much.
 */
public interface SlabbedRenderStateDy {
    double slabbed$renderDy();

    void slabbed$setRenderDy(double dy);
}
