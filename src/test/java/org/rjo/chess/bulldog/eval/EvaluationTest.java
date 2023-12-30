package org.rjo.chess.bulldog.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.rjo.chess.bulldog.board.Board.Square;
import org.rjo.chess.bulldog.game.Fen;
import org.rjo.chess.bulldog.game.Game;
import org.rjo.chess.bulldog.piece.Colour;
import org.rjo.chess.bulldog.piece.Piece;

public class EvaluationTest {

   @Test
   public void squareValues() {
      assertEquals(230, Piece.PAWN.calculatePieceSquareValue(Square.c2.index(), Colour.WHITE)
            + Piece.PAWN.calculatePieceSquareValue(Square.d4.index(), Colour.WHITE));
      assertEquals(230, Piece.PAWN.calculatePieceSquareValue(Square.c7.index(), Colour.BLACK)
            + Piece.PAWN.calculatePieceSquareValue(Square.d5.index(), Colour.BLACK));

      for (Square sq : Square.values()) {
         System.out.println("queen " + sq + ": " + Piece.QUEEN.calculatePieceSquareValue(sq.index(), Colour.BLACK));
         System.out.println("pawn " + sq + ": " + Piece.PAWN.calculatePieceSquareValue(sq.index(), Colour.BLACK));
         System.out.println(sq + ": " + Piece.KING.calculatePieceSquareValue(sq.index(), Colour.BLACK));
      }
   }

   @Test
   public void pawnPieceValue() {
      assertEquals(230, Piece.PAWN.calculatePieceSquareValue(Square.c2.index(), Colour.WHITE)
            + Piece.PAWN.calculatePieceSquareValue(Square.d4.index(), Colour.WHITE));
      assertEquals(230, Piece.PAWN.calculatePieceSquareValue(Square.c7.index(), Colour.BLACK)
            + Piece.PAWN.calculatePieceSquareValue(Square.d5.index(), Colour.BLACK));
   }

   @Test
   public void queenPieceValue() {
      assertEquals(905, Piece.QUEEN.calculatePieceSquareValue(Square.c2.index(), Colour.WHITE));
      assertEquals(890, Piece.QUEEN.calculatePieceSquareValue(Square.a6.index(), Colour.BLACK));
   }

   @Test
   public void mateInOne() {
      Game game = Fen.decode("r3k3/pppppp2/8/8/8/8/8/4K2R w - - 0 2");
      SearchStrategy strat = new AlphaBeta3(System.out);
      MoveInfo m = strat.findMove(game.getPosition());
      assertEquals("h1-h8+", m.getMove().toString());
   }

   @Test
   public void mateInTwo() {
      Game game = Fen.decode("4k3/3ppp2/5n2/6KR/8/8/8/8 w - - 0 2");
      SearchStrategy strat = new AlphaBeta3(System.out);
      MoveInfo m = strat.findMove(game.getPosition());
      assertEquals("h5-h8+", m.getMove().toString());
   }

   @Test
   public void opponentMateInOne() {
      Game game = Fen.decode("4k3/8/8/8/8/4P1PP/3PrPPP/7K b - - 0 1 ");
      SearchStrategy strat = new AlphaBeta3(System.out);
      MoveInfo m = strat.findMove(game.getPosition());
      assertEquals("e2-e1+", m.getMove().toString());
   }

   @Test
   public void mateInOneBetterThanMateInTwo() {
      Game game = Fen.decode("r1r3k1/5ppp/2R5/2R5/8/8/1B6/3K2Q1 w - - 0 15");
      SearchStrategy strat = new AlphaBeta3(System.out);
      MoveInfo m = strat.findMove(game.getPosition());
      assertEquals("g1xg7+", m.getMove().toString());
   }
}
