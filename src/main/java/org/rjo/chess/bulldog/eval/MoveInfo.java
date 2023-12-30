package org.rjo.chess.bulldog.eval;

import org.rjo.chess.bulldog.move.IMove;

public class MoveInfo {
   private IMove move;
   private Line line;
   private boolean checkmate;
   private boolean stalemate;

   public void setCheckmate(boolean b) { this.checkmate = b; }

   public boolean isCheckmate() { return checkmate; }

   public boolean isStalemate() { return stalemate; }

   public void setStalemate(boolean b) { this.stalemate = b; }

   public IMove getMove() { return move; }

   public void setMove(IMove move) { this.move = move; }

   public Line getLine() { return line; }

   public void setLine(Line line) { this.line = line; }

   @Override
   public String toString() {
      return move + "(" + line + ")";
   }

}