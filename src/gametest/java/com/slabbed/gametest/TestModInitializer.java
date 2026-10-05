package com.slabbed.gametest;

/**
 * Test-content initialisers: the suite's own blocks and fixtures, run once when the test mod is
 * constructed (the Fabric lines list these as {@code main} entrypoints of the test mod).
 */
public interface TestModInitializer {
    void onInitialize();
}
