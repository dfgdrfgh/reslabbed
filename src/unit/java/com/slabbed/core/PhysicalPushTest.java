package com.slabbed.core;

import java.util.ArrayList;
import java.util.List;

/** Standalone physical piston preparation assertions; no Minecraft runtime is required. */
public final class PhysicalPushTest {
    private static int checked;

    public static void main(String[] args) {
        for (var direction : PhysicalPush.Direction6.values()) {
            var a = piece(-7, -9, -5, 2, "a", true);
            var b = translated(a, direction, 1, "b");
            var c = translated(a, direction, 2, "c");
            var world = List.of(a, b, c);
            var reads = new int[1];
            PhysicalPush.Reader<String> reader = bounds -> {
                reads[0]++;
                return world; // Cell readers may include companions and unrelated halves.
            };
            var plan = PhysicalPush.plan(List.of(a), direction, reader);
            check(plan.success(), direction + " complete closure succeeds");
            check(plan.moves().size() == 3, direction + " includes every blocked destination");
            check(plan.moves().get(0).source().equals(c), direction + " farthest moves first");
            check(plan.moves().get(2).source().equals(a), direction + " seed moves last");
            check(plan.moves().get(2).destination().equals(b.bounds()), direction + " translates exactly one block");
            check(reads[0] == 3, direction + " one destination read per logical piece");
            check(world.equals(List.of(a, b, c)), direction + " preparation never changes the world");
            rejectMutation(() -> plan.moves().clear());
            var stop = piece(c.x(), c.z(), c.originHalf(), c.length(), "c", false);
            var blocked = PhysicalPush.plan(List.of(a), direction, bounds -> List.of(a, b, stop));
            check(blocked.failure() == PhysicalPush.Failure.IMMOVABLE, direction + " stops at immovable destination");
            check(blocked.moves().isEmpty(), direction + " failure contains no partial moves");
            var chain = new ArrayList<PhysicalPush.Piece<String>>();
            for (int n = 0; n < 12; n++) chain.add(translated(a, direction, n, "chain" + n));
            var twelve = PhysicalPush.plan(List.of(chain.getFirst()), direction, bounds -> chain);
            check(twelve.success() && twelve.moves().size() == 12, direction + " twelve pieces accepted");
            chain.add(translated(a, direction, 12, "thirteenth"));
            var thirteen = PhysicalPush.plan(List.of(chain.getFirst()), direction, bounds -> chain);
            check(thirteen.failure() == PhysicalPush.Failure.LIMIT && thirteen.moves().isEmpty(), direction + " thirteenth rejects transaction");
        }

        var crossing = piece(0, 0, -1, 2, "crossing", true);
        var blocker = piece(1, 0, -1, 2, "blocker", true);
        var duplicates = PhysicalPush.plan(List.of(crossing, crossing), PhysicalPush.Direction6.EAST,
                bounds -> List.of(crossing, crossing, blocker, blocker));
        check(duplicates.success() && duplicates.moves().size() == 2, "replicated companion fragments count once");
        var conflict = piece(1, 0, -1, 2, "wrong copy", true);
        var invalid = PhysicalPush.plan(List.of(crossing), PhysicalPush.Direction6.EAST,
                bounds -> List.of(blocker, conflict));
        check(invalid.failure() == PhysicalPush.Failure.CONFLICT && invalid.moves().isEmpty(), "conflicting companion values reject all moves");
        var immovableCopy = piece(1, 0, -1, 2, "blocker", false);
        var inconsistent = PhysicalPush.plan(List.of(crossing), PhysicalPush.Direction6.EAST,
                bounds -> List.of(blocker, immovableCopy));
        check(inconsistent.failure() == PhysicalPush.Failure.CONFLICT && inconsistent.moves().isEmpty(), "conflicting companion mobility rejects all moves");
        var lower = piece(0, 0, -2, 1, "slab", false);
        var upper = piece(-1, 0, -1, 2, "upper", true);
        var shared = PhysicalPush.plan(List.of(upper), PhysicalPush.Direction6.EAST, bounds -> List.of(lower));
        check(shared.success() && shared.moves().size() == 1, "stationary lower slab sharing destination cell is untouched");
        var tangentX = piece(1, 0, -1, 2, "touch x", false);
        var tangentZ = piece(0, 1, -1, 2, "touch z", false);
        var empty = PhysicalPush.plan(List.of(crossing), PhysicalPush.Direction6.UP,
                bounds -> List.of(tangentX, tangentZ, crossing));
        check(empty.success() && empty.moves().size() == 1, "exclusive X Z faces do not collide");
        var yTouch = piece(0, 0, 3, 1, "touch y", false);
        check(PhysicalPush.plan(List.of(crossing), PhysicalPush.Direction6.UP, bounds -> List.of(yTouch)).success(), "exclusive half Y face does not collide");
        var singleImmovable = PhysicalPush.plan(List.of(lower), PhysicalPush.Direction6.DOWN, bounds -> List.of());
        check(singleImmovable.failure() == PhysicalPush.Failure.IMMOVABLE && singleImmovable.moves().isEmpty(), "immovable seed rejects preparation");
        var seedConflict = PhysicalPush.plan(List.of(crossing, piece(0,0,-1,2,"conflict",true)), PhysicalPush.Direction6.UP, bounds -> List.of());
        check(seedConflict.failure() == PhysicalPush.Failure.CONFLICT, "conflicting seed copies reject preparation");
        for (var direction : PhysicalPush.Direction6.values()) {
            var half=piece(-3,-4,-7,1,"half",true);
            var next=translated(half,direction,1,"next half");
            var halfPlan=PhysicalPush.plan(List.of(half),direction,bounds->List.of(half,next,next));
            check(halfPlan.success() && halfPlan.moves().size()==2,direction+" one-half pieces form a complete closure");
            check(halfPlan.moves().getFirst().source().equals(next),direction+" one-half pieces preserve farthest ordering");
            check(halfPlan.moves().getLast().destination().maxHalf()-halfPlan.moves().getLast().destination().minHalf()==1,
                    direction+" translated slab retains its physical length");
        }
        var twelveCopies=new ArrayList<PhysicalPush.Piece<String>>();
        for (int n=0;n<12;n++) {
            var logical=translated(crossing,PhysicalPush.Direction6.EAST,n,"replicated"+n);
            twelveCopies.add(logical);twelveCopies.add(logical);
        }
        var replicatedLimit=PhysicalPush.plan(List.of(twelveCopies.getFirst()),PhysicalPush.Direction6.EAST,bounds->twelveCopies);
        check(replicatedLimit.success() && replicatedLimit.moves().size()==12,"twenty-four fragments represent twelve whole pieces");

        var sticky = piece(0, 1, -1, 2, "validated adhesion", true);
        var adhesion = PhysicalPush.plan(List.of(crossing), PhysicalPush.Direction6.EAST, bounds -> List.of(),
                (source, direction) -> source.equals(crossing) ? List.of(sticky) : List.of(crossing));
        check(adhesion.success() && adhesion.moves().size() == 2, "validated adhesion edges expand closure and terminate cycles");
        var adhesionBlocked=PhysicalPush.plan(List.of(crossing),PhysicalPush.Direction6.EAST,bounds->List.of(),
                (source,direction)->List.of(lower));
        check(adhesionBlocked.failure()==PhysicalPush.Failure.IMMOVABLE && adhesionBlocked.moves().isEmpty(),
                "immovable validated adhesion branch rejects the entire plan");
        check(PhysicalPush.plan(List.of(crossing), PhysicalPush.Direction6.EAST, bounds -> List.of()).moves().size() == 1, "default planner invents no adhesion");
        check(PhysicalPush.plan(List.<PhysicalPush.Piece<String>>of(), PhysicalPush.Direction6.EAST, bounds -> List.of()).success(), "empty preparation succeeds");
        rejectInput(() -> piece(0,0,0,0,"invalid",true));
        rejectInput(() -> piece(0,0,0,3,"invalid",true));
        check(crossing.key().equals(new PhysicalPush.SpatialKey(0,0,-1,2)), "spatial identity excludes duplicated values");
        System.out.println("PHYSICAL_PUSH_PASS " + checked + " assertions");
    }

    private static PhysicalPush.Piece<String> piece(int x,int z,int half,int length,String value,boolean movable) {
        return new PhysicalPush.Piece<>(x,z,half,length,value,movable);
    }
    private static PhysicalPush.Piece<String> translated(PhysicalPush.Piece<String> a,PhysicalPush.Direction6 d,int n,String value) {
        return piece(a.x()+d.dx()*n,a.z()+d.dz()*n,a.originHalf()+d.halfDy()*n,a.length(),value,true);
    }
    private static void check(boolean condition,String message) {
        checked++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejectMutation(Runnable operation) {
        try { operation.run(); } catch (UnsupportedOperationException expected) { checked++; return; }
        throw new AssertionError("result moves are mutable");
    }
    private static void rejectInput(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checked++; return; }
        throw new AssertionError("invalid piece accepted");
    }
}
