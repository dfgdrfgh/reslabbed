package com.slabbed.hanging;

/** An entity owns its remembered mount; geometry reads never ask the backing block for a new seat. */
public interface HangingSeat {
    double slabbed$hangingOffset();
}
