package org.rjo.chess.bulldog.eval;

import java.util.ArrayDeque;
import java.util.Deque;

import org.rjo.chess.bulldog.move.IMove;

public class Line {
   private Deque<IMove> moves;

   public Line() {
      this.moves = new ArrayDeque<>();
   }

   public Line(IMove m, int startDepth) {
      this();
      addMove(m, startDepth);
   }

   // copy constructor
   public Line(Line line) {
      this();
      moves.addAll(line.moves);
   }

   public void addMove(IMove m, int startDepth) {
      moves.add(m);
      if (moves.size() > startDepth + 1) { throw new RuntimeException("moves too long (startDepth: " + startDepth + "): " + moves); }
   }

   public void removeLastMove() {
      moves.removeLast();
   }

   public Deque<IMove> getMoves() { return moves; }

   @Override
   public String toString() {
      return moves.toString();
   }
}