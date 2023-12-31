package org.rjo.chess.bulldog.eval;

import java.util.List;

import org.rjo.chess.bulldog.game.Position;
import org.rjo.chess.bulldog.move.IMove;

public class Negamax implements SearchStrategy {
   private static final int MIN_INT = Integer.MIN_VALUE + 5;

   private int nbrNodesEvaluated;

   int depth = 4;

   @Override
   public int getCurrentDepth() { return depth; }

   @Override
   public void incrementDepth(int increment) {
      depth += increment;
   }

   @Override
   public MoveInfo findMove(Position posn) {
      nbrNodesEvaluated = 0;
      int max = MIN_INT;
      MoveInfo moveInfo = new MoveInfo();
      long overallStartTime = System.currentTimeMillis();
      List<IMove> moves = posn.findMoves(posn.getSideToMove());
      for (IMove move : moves) {
         long startTime = System.currentTimeMillis();
         Position posnAfterMove = posn.move(move);
         int score = -negaMax(depth - 1, posnAfterMove);
         // System.out.println(Fen.encode(game) + ", score=" + score +
         // ",depth=" + depth + ",max=" + max);
         if (score > max) {
            max = score;
            moveInfo.setMove(move);
            System.out.println("******   (" + move + ": " + score + ")");
         }
         System.out.println(String.format("(%7s,%5d,%7d,%5dms)", move, score, nbrNodesEvaluated, (System.currentTimeMillis() - startTime)));
      }
      long overallStopTime = System.currentTimeMillis();
      System.out.println(String.format("time: %7.2fs, %9.2f nodes/s", (overallStopTime - overallStartTime) / 1000.0,
            (1.0 * nbrNodesEvaluated / (overallStopTime - overallStartTime)) * 1000));
      return moveInfo;
   }

   private int negaMax(int depth, Position posn) {
      if (depth == 0) {
         nbrNodesEvaluated++;
         return posn.evaluate();
      }
      int max = MIN_INT;
      List<IMove> moves = posn.findMoves(posn.getSideToMove());
      for (IMove move : moves) {
         Position posnAfterMove = posn.move(move);
         int score = -negaMax(depth - 1, posnAfterMove);
         // System.out
         // .println(game.getSideToMove() + ": " + move + ", score=" + score
         // + ",depth=" + depth + ",max=" + max);
         if (score > max) { max = score; }
      }
      return max;
   }
}
