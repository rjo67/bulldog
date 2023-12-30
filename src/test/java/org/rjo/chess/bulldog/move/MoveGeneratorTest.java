package org.rjo.chess.bulldog.move;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rjo.chess.bulldog.bits.BitSetFactory;
import org.rjo.chess.bulldog.bits.BitSetUnifier;
import org.rjo.chess.bulldog.board.Board.Square;
import org.rjo.chess.bulldog.game.Position;
import org.rjo.chess.bulldog.piece.Colour;
import org.rjo.chess.bulldog.piece.Piece;

public class MoveGeneratorTest {

   private MoveGenerator movegen;
   private List<IMove> moves;

   @BeforeEach
   public void setup() {
      movegen = new MoveGenerator();
      moves = new ArrayList<>();
   }

   @Test
   public void kingsMove() {
      var posn = new Position(Square.e3, Square.b8);
      posn.addPiece(Colour.WHITE, Piece.ROOK, Square.a1);
      posn.addPiece(Colour.BLACK, Piece.BISHOP, Square.d2);

      BitSetUnifier forbiddenMoves = BitSetFactory.createBitSet(64);
      long start = System.currentTimeMillis();
      for (int i = 0; i < 1_000_000; i++) {
         moves = new ArrayList<>();
         movegen.generateKingMoves(posn, Square.e3.index(), Colour.WHITE, moves, forbiddenMoves);
         assertEquals(8, moves.size());
      }
      System.out.println("kingsMove: " + (System.currentTimeMillis() - start)); // 1_000_000 times: 100-140ms
   }

}
