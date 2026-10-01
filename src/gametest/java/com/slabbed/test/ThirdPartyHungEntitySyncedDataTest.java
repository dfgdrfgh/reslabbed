package com.slabbed.test;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A hung entity from another mod must still be constructible. Slabbed's remembered seat is a synced
 * slot allocated on {@code HangingEntity}, so it falls inside every hung class's id range; a class
 * whose synched-data hook never forwards the declaration (an empty hook, or one that only defines
 * its own values) used to leave a hole there, and building its synched data threw before the
 * entity existed. Such a class keeps vanilla behaviour: no seat is declared or honoured for it.
 *
 * <p>MUTATIONS each row names, per LAW.md's reachability rule.
 * <ul>
 *   <li>Remove {@code SynchedEntityDataBuilderHangSeatMixin} from the mixin config: both
 *       third-party rows redden (construction throws "has not defined synched data value").</li>
 *   <li>Make {@code slabbed$reserveUndeclaredHangSeatSlot} also set the declared flag: the
 *       third-party rows redden on "an undeclared class keeps vanilla behaviour".</li>
 *   <li>Make the builder fill run for every hung class, overwriting a declared slot: construction of
 *       a vanilla painting throws a duplicate-id error and the painting row reddens.</li>
 * </ul>
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class ThirdPartyHungEntitySyncedDataTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void hungEntityWithEmptySyncedDataHookBuilds(GameTestHelper ctx) {
        QuietHung entity = build(ctx, () -> new QuietHung(ctx.getLevel()));
        requireUndeclared(ctx, entity);
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void hungEntityWithItsOwnSyncedDataBuilds(GameTestHelper ctx) {
        OwnDataHung entity = build(ctx, () -> new OwnDataHung(ctx.getLevel()));
        ctx.assertTrue(entity.getEntityData().get(OwnDataHung.OWN_VALUE) == 7,
                "the class's own synced value must keep its default, got " + entity.getEntityData().get(OwnDataHung.OWN_VALUE));
        requireUndeclared(ctx, entity);
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void vanillaPaintingStillCarriesItsSeat(GameTestHelper ctx) {
        Painting painting = build(ctx, () -> new Painting(EntityType.PAINTING, ctx.getLevel()));
        HangingSeatDyHolder holder = (HangingSeatDyHolder) painting;
        holder.slabbed$restoreHangSeatDy(-0.5d);
        ctx.assertTrue(holder.slabbed$hasHangSeat() && Math.abs(holder.slabbed$hangSeatDy() + 0.5d) <= EPS,
                "a vanilla painting must still store a restored seat, got " + holder.slabbed$hangSeatDy());
        ctx.succeed();
    }

    private static <T extends Entity> T build(GameTestHelper ctx, java.util.function.Supplier<T> factory) {
        try {
            return factory.get();
        } catch (RuntimeException e) {
            ctx.fail("constructing the hung entity threw: " + e);
            throw e;
        }
    }

    private static void requireUndeclared(GameTestHelper ctx, Entity entity) {
        HangingSeatDyHolder holder = (HangingSeatDyHolder) entity;
        holder.slabbed$restoreHangSeatDy(-0.5d);
        ctx.assertTrue(!holder.slabbed$hasHangSeat() && Math.abs(holder.slabbed$hangSeatDy()) <= EPS,
                "an undeclared class keeps vanilla behaviour: no seat is stored, got " + holder.slabbed$hangSeatDy());
    }

    /** A hung class whose synched-data hook defines nothing and does not forward to Slabbed. */
    private static class QuietHung extends HangingEntity {
        QuietHung(Level level) {
            super(EntityType.PAINTING, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
        }

        @Override
        protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
            return new AABB(pos);
        }

        @Override
        public void playPlacementSound() {
        }

        @Override
        public void dropItem(Entity breaker) {
        }
    }

    /** A hung class that defines only its own synced value, allocated after Slabbed's slot. */
    private static final class OwnDataHung extends QuietHung {
        static final EntityDataAccessor<Integer> OWN_VALUE =
                SynchedEntityData.defineId(OwnDataHung.class, EntityDataSerializers.INT);

        OwnDataHung(Level level) {
            super(level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
            builder.define(OWN_VALUE, 7);
        }
    }
}
