package com.slabbed.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Nonmutating whole-piece piston preparation over physical half-block bounds (LAW.md, DY_SPEC.md). */
public final class PhysicalPush {
    public static final int LIMIT = 12;

    private PhysicalPush() {}

    public enum Direction6 {
        EAST(1,0,0), WEST(-1,0,0), UP(0,2,0), DOWN(0,-2,0), SOUTH(0,0,1), NORTH(0,0,-1);
        private final int dx, halfDy, dz;
        Direction6(int dx,int halfDy,int dz) { this.dx=dx; this.halfDy=halfDy; this.dz=dz; }
        public int dx() { return dx; }
        public int halfDy() { return halfDy; }
        public int dz() { return dz; }
    }

    public record SpatialKey(int x,int z,int originHalf,int length) {}

    /** X/Z are integer cell coordinates; Y is in half blocks. Every maximum is exclusive. */
    public record Bounds(int minX,int minHalf,int minZ,int maxX,int maxHalf,int maxZ) {
        public Bounds {
            if (minX>=maxX || minHalf>=maxHalf || minZ>=maxZ)
                throw new IllegalArgumentException("bounds must have positive volume");
        }
        public boolean intersects(Bounds other) {
            return minX<other.maxX && maxX>other.minX && minHalf<other.maxHalf
                    && maxHalf>other.minHalf && minZ<other.maxZ && maxZ>other.minZ;
        }
    }

    public record Piece<T>(int x,int z,int originHalf,int length,T value,boolean movable) {
        public Piece {
            if (length<1 || length>2) throw new IllegalArgumentException("piece length must be one or two halves");
            Objects.requireNonNull(value,"piece value");
        }
        public SpatialKey key() { return new SpatialKey(x,z,originHalf,length); }
        public Bounds bounds() {
            return new Bounds(x,originHalf,z,Math.addExact(x,1),Math.addExact(originHalf,length),Math.addExact(z,1));
        }
        public Bounds destination(Direction6 direction) {
            int movedX=Math.addExact(x,direction.dx()), movedZ=Math.addExact(z,direction.dz());
            int movedHalf=Math.addExact(originHalf,direction.halfDy());
            return new Bounds(movedX,movedHalf,movedZ,Math.addExact(movedX,1),
                    Math.addExact(movedHalf,length),Math.addExact(movedZ,1));
        }
    }

    @FunctionalInterface public interface Reader<T> {
        /** May over-return full-cell occupants and repeated companion fragments. */
        Collection<Piece<T>> occupants(Bounds destination);
    }

    @FunctionalInterface public interface Adhesion<T> {
        /** Supplies only edges already accepted by the caller's vanilla slime/honey rules. */
        Collection<Piece<T>> attached(Piece<T> piece,Direction6 direction);
    }

    public record Move<T>(Piece<T> source,Bounds destination) {}
    public enum Failure { NONE, IMMOVABLE, CONFLICT, LIMIT }
    public record Result<T>(List<Move<T>> moves,Failure failure) {
        public Result {
            moves=List.copyOf(moves);
            Objects.requireNonNull(failure,"failure");
            if (failure!=Failure.NONE && !moves.isEmpty())
                throw new IllegalArgumentException("failed plans cannot contain moves");
        }
        public boolean success() { return failure==Failure.NONE; }
    }

    public static <T> Result<T> plan(Collection<Piece<T>> seeds,Direction6 direction,Reader<T> reader) {
        return plan(seeds,direction,reader,(piece,pushDirection)->List.of());
    }

    public static <T> Result<T> plan(Collection<Piece<T>> seeds,Direction6 direction,Reader<T> reader,Adhesion<T> adhesion) {
        Objects.requireNonNull(direction,"direction");
        Objects.requireNonNull(reader,"reader");
        Objects.requireNonNull(adhesion,"adhesion");
        var pieces=new LinkedHashMap<SpatialKey,Piece<T>>();
        var pending=new ArrayDeque<Piece<T>>();
        for (var seed : seeds) {
            var failure=include(seed,pieces,pending);
            if (failure!=Failure.NONE) return failed(failure);
        }
        while (!pending.isEmpty()) {
            var piece=pending.removeFirst();
            var destination=piece.destination(direction);
            for (var occupant : reader.occupants(destination)) {
                if (!destination.intersects(occupant.bounds())) continue;
                var failure=include(occupant,pieces,pending);
                if (failure!=Failure.NONE) return failed(failure);
            }
            for (var attached : adhesion.attached(piece,direction)) {
                var failure=include(attached,pieces,pending);
                if (failure!=Failure.NONE) return failed(failure);
            }
        }
        var ordered=new ArrayList<>(pieces.values());
        ordered.sort(Comparator.<Piece<T>>comparingLong(piece->projection(piece,direction)).reversed());
        var moves=new ArrayList<Move<T>>(ordered.size());
        for (var piece : ordered) moves.add(new Move<>(piece,piece.destination(direction)));
        return new Result<>(moves,Failure.NONE);
    }

    private static <T> Failure include(Piece<T> piece,LinkedHashMap<SpatialKey,Piece<T>> pieces,ArrayDeque<Piece<T>> pending) {
        Objects.requireNonNull(piece,"piece");
        var previous=pieces.get(piece.key());
        if (previous!=null) return previous.equals(piece)?Failure.NONE:Failure.CONFLICT;
        if (!piece.movable()) return Failure.IMMOVABLE;
        if (pieces.size()==LIMIT) return Failure.LIMIT;
        pieces.put(piece.key(),piece);
        pending.addLast(piece);
        return Failure.NONE;
    }

    private static long projection(Piece<?> piece,Direction6 direction) {
        return (long)piece.x()*direction.dx()*2+(long)piece.originHalf()*direction.halfDy()
                +(long)piece.z()*direction.dz()*2;
    }
    private static <T> Result<T> failed(Failure failure) { return new Result<>(List.of(),failure); }
}
