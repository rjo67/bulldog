package org.rjo.chess.bulldog.eval;

import org.rjo.chess.bulldog.move.IMove;

public class MoveEval {
   public MoveEval(int value, IMove move) {
      this.value = value;
      this.move = move;
   }

   private int value;
   private IMove move;

   public int getValue() { return value; }

   public IMove getMove() { return move; }

   @Override
   public String toString() {
      return move.toString() + ":" + value;
   }
}
