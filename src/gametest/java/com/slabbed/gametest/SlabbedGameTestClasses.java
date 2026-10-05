package com.slabbed.gametest;

import java.util.List;

/**
 * The registered test classes and the test-content initialisers, in registration order. This list
 * replaces the Fabric test mod's entrypoint declarations; tools/expected-gametest-count.py reads
 * CLASSES (and CLASSES_26_3 for a 26.3 build) to compute the suite count the run must report.
 */
public final class SlabbedGameTestClasses {
    private SlabbedGameTestClasses() {
    }

    public static final List<String> CLASSES = List.of(
            "com.slabbed.test.ScaffoldingLoweredStandTest",
            "com.slabbed.test.ChainUnderLoweredFullBlockCapTest",
            "com.slabbed.test.GhostLoweredCollisionProofTest",
            "com.slabbed.test.ItemFrameWysiwygBoxTest",
            "com.slabbed.test.HangingSeatRememberedTest",
            "com.slabbed.test.HangingLoadDeferralTest",
            "com.slabbed.test.TrampledSupportConversionTest",
            "com.slabbed.test.Slabbed2612LoweringContractTest",
            "com.slabbed.test.Slabbed2612DyFingerprintTest",
            "com.slabbed.test.Slabbed2612UseOnPlacementTest",
            "com.slabbed.test.Slabbed2612RestingDyTest",
            "com.slabbed.test.Slabbed2612ConnectorSurvivalTest",
            "com.slabbed.test.Slabbed2612CollisionDepthTest",
            "com.slabbed.test.Slabbed2612CompoundMatrixTest",
            "com.slabbed.test.RedstoneGateOnTerrainSlabsPlacementTest",
            "com.slabbed.test.SlabHeightStepCullGh24Test",
            "com.slabbed.test.SlabOnSlabVerticalAnchorTest",
            "com.slabbed.test.DecorativeObjectSupportAnchorTest",
            "com.slabbed.test.SlabVerticalSupportTerrainSlabsGuardTest",
            "com.slabbed.test.FloorTorchSupportDyTest",
            "com.slabbed.test.BottomSlabSpawnProofTest",
            "com.slabbed.test.CompatEligibilityPredicateTest",
            "com.slabbed.test.CeilingHungDecorationFreezeAnchorTest",
            "com.slabbed.test.FenceWallVisibleSupportDyTest",
            "com.slabbed.test.CantileverBfsTerrainSlabsGuardTest",
            "com.slabbed.test.TerrainSlabsOwnSlabDyGuardTest",
            "com.slabbed.test.SlabLaneConduitTerrainSlabsGuardTest",
            "com.slabbed.test.SlabdyRowFormatterFieldsTest",
            "com.slabbed.test.ShippedDebugCommandsTest",
            "com.slabbed.test.client.BetaNoticeSessionGateTest",
            "com.slabbed.test.client.BetaNoticeDismissedWorldsTest",
            "com.slabbed.test.client.BetaNoticeDismissFeedbackTest",
            "com.slabbed.test.client.BetaNoticeVersionChannelTest",
            "com.slabbed.test.RenderRegionBoundaryReadTest",
            "com.slabbed.test.EnvironmentFillEligibilityTest",
            "com.slabbed.test.CompatOffsetDeferralPinTest",
            "com.slabbed.test.LoweredSeatFreezeTest",
            "com.slabbed.test.FloorMountedIsNotHangingTest",
            "com.slabbed.test.ReplaceableCellSeatTest",
            "com.slabbed.test.ThinLayerOwnCellPlacementTest",
            "com.slabbed.test.WallSignAboveSlabTest",
            "com.slabbed.test.RelocatedPlacementAimTest",
            "com.slabbed.test.BottomSlabLoweredByCarrierBelowTest",
            "com.slabbed.test.BlockEntityNeverPopTest",
            "com.slabbed.test.BlockEntityCantileverTest",
            "com.slabbed.test.BlockEntityCantileverTerrainSlabsGuardTest",
            "com.slabbed.test.GeometricRemeshSchedulerTest",
            "com.slabbed.test.LiveCursorIntentRecorderCaptureGameTest",
            "com.slabbed.test.RecorderManifestRedactionTest",
            "com.slabbed.test.DoubleSlabPlacementNonDestructiveTest",
            "com.slabbed.test.ChainCeilingBridgeTextureTest",
            "com.slabbed.test.ModelStaleSentinelContractTest",
            "com.slabbed.test.ModelStaleSentinelAllocationGateTest",
            "com.slabbed.test.FrozenStoreCollisionAllocationGateTest",
            "com.slabbed.test.FrozenStoreRenderReadAllocationGateTest",
            "com.slabbed.test.RecorderUpgradeContractTest",
            "com.slabbed.test.EnsembleCoherenceContractTest",
            "com.slabbed.test.EnsemblePlacementCoherenceTest",
            "com.slabbed.test.StateChangeAnchorTest",
            "com.slabbed.test.DepartedOccupantFactClearTest",
            "com.slabbed.test.HauntedCarrierCellTest",
            "com.slabbed.test.ConnectingStructuralFreezeTest",
            "com.slabbed.test.AnchoredDepthReadbackTest",
            "com.slabbed.test.CeilingFlushRulingTest",
            "com.slabbed.test.UndersideSeatClampTest",
            "com.slabbed.test.WaterlogReadSymmetryTest",
            "com.slabbed.test.WysiwygMarkerHandOffTest",
            "com.slabbed.test.OffsetRaycastDeepLaneTest",
            "com.slabbed.test.NeighborUpdateInvarianceTest",
            "com.slabbed.test.DeepCompoundTowerLawTest",
            "com.slabbed.test.LandingRuleLawTest",
            "com.slabbed.test.UpwardContinuationValidationTest",
            "com.slabbed.test.DeepObjectOwnerHitRescueTest",
            "com.slabbed.test.HangingUndersideHitRescueTest",
            "com.slabbed.test.PlacementCaptureBoundaryGameTest",
            "com.slabbed.test.RegistrySweepTest",
            "com.slabbed.test.SlabTestKitPaletteTest",
            "com.slabbed.test.anchor.FrozenFlatAttachmentCapacityTest",
            "com.slabbed.test.anchor.PlacementDyAttachmentCapacityTest",
            "com.slabbed.test.PistonPlacementDyTransferTest",
            "com.slabbed.test.FrozenDyModePayloadTest",
            "com.slabbed.test.MinecartRailSeatDepthTest",
            "com.slabbed.test.RailSlopeProfileTest",
            "com.slabbed.test.RailVisualSignalTest",
            "com.slabbed.test.RedstoneWireVisualStepTest",
            "com.slabbed.test.ArmorStandVisibleTopPlacementTest",
            "com.slabbed.test.BoatVisibleSurfacePlacementTest",
            "com.slabbed.test.config.SlabbedConfigFileTest",
            "com.slabbed.test.PotSeatOptionTest",
            "com.slabbed.test.ManualDyAdjustLawTest"
    );

    /**
     * Registered only on 26.3 and newer: the cushion entity exists from 26.3, and the /slabrig family and
     * the kit command test are cataloged against 26.3 (route indices, SignTextSlot). The build compiles
     * these sources only for that version, so they are listed apart and joined by {@link #classes()}.
     */
    public static final List<String> CLASSES_26_3 = List.of(
            "com.slabbed.test.CushionOnLoweredBlockTest",
            "com.slabbed.test.SlabRigCommandSmokeTest",
            "com.slabbed.test.SlabRigCaseCatalogTest",
            "com.slabbed.test.SlabRigHangingCatalogTest",
            "com.slabbed.test.SlabRigHangingPaintingPlanTest",
            "com.slabbed.test.SlabRigHangingKernelArtifactsTest",
            "com.slabbed.test.SlabRigHangingPaintingKernelTest",
            "com.slabbed.test.SlabRigHangingDirectEntityGateTest",
            "com.slabbed.test.SlabRigHangingDirectFixtureTest",
            "com.slabbed.test.SlabRigHangingDirectActionsTest",
            "com.slabbed.test.SlabRigHangingDirectStateStoreTest",
            "com.slabbed.test.SlabRigHangingDirectExecutorTest",
            "com.slabbed.test.SlabTestKitCommandsTest"
    );

    public static final List<String> INITIALIZERS = List.of(
            "com.slabbed.test.RedstoneGateOnTerrainSlabsPlacementTest$TerrainSlabsRedstoneGateTestEntrypoint",
            "com.slabbed.test.SlabVerticalSupportTerrainSlabsGuardTest$TerrainSlabsVerticalSupportGuardTestEntrypoint",
            "com.slabbed.test.FloorTorchSupportDyTest$TerrainSlabsFloorTorchSupportGuardTestEntrypoint",
            "com.slabbed.test.FenceWallVisibleSupportDyTest$TerrainSlabsFenceWallSupportGuardTestEntrypoint",
            "com.slabbed.test.CantileverBfsTerrainSlabsGuardTest$TerrainSlabsCantileverBfsGuardTestEntrypoint",
            "com.slabbed.test.TerrainSlabsOwnSlabDyGuardTest$TerrainSlabsOwnSlabDyGuardTestEntrypoint",
            "com.slabbed.test.CompatOffsetDeferralPinTest$CompatOffsetDeferralPinTestEntrypoint",
            "com.slabbed.test.SlabLaneConduitTerrainSlabsGuardTest$TerrainSlabsSlabLaneConduitGuardTestEntrypoint",
            "com.slabbed.test.BlockEntityCantileverTerrainSlabsGuardTest$TerrainSlabsBlockEntityCantileverGuardTestEntrypoint",
            "com.slabbed.test.GeometricRemeshSchedulerTest$GeometricRemeshSchedulerTestEntrypoint",
            "com.slabbed.test.PlacementCaptureBoundaryGameTest$TestContentEntrypoint",
            "com.slabbed.test.BottomSlabSpawnProofTest$TerrainSlabsSpawnProofTestEntrypoint",
            "com.slabbed.test.CompatEligibilityPredicateTest$CompatEligibilityFixtureEntrypoint"
    );

    /** Initialisers that belong to the 26.3-only classes above. */
    public static final List<String> INITIALIZERS_26_3 = List.of(
            "com.slabbed.test.SlabRigHangingDirectExecutorTest$StoreBootstrap"
    );

    /** The classes to register on the running game version. */
    public static List<String> classes() {
        return join(CLASSES, CLASSES_26_3);
    }

    /** The initialisers to run on the running game version. */
    public static List<String> initializers() {
        return join(INITIALIZERS, INITIALIZERS_26_3);
    }

    private static List<String> join(List<String> always, List<String> modern) {
        if (!com.slabbed.compat.MinecraftVersions.AT_LEAST_26_3) {
            return always;
        }
        java.util.ArrayList<String> all = new java.util.ArrayList<>(always);
        all.addAll(modern);
        return List.copyOf(all);
    }
}
