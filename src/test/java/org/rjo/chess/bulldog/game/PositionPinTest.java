package org.rjo.chess.bulldog.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rjo.chess.bulldog.TestUtil;
import org.rjo.chess.bulldog.bits.BitSetFactory;
import org.rjo.chess.bulldog.bits.BitSetUnifier;
import org.rjo.chess.bulldog.board.Board.Square;
import org.rjo.chess.bulldog.move.MoveGenerator;
import org.rjo.chess.bulldog.piece.Colour;
import org.rjo.chess.bulldog.piece.Piece;

public class PositionPinTest {

   private BitSetUnifier pinMaskHV;
   private BitSetUnifier pinMaskDiag;

   @BeforeEach
   public void setup() {
      pinMaskHV = BitSetFactory.createBitSet(64);
      pinMaskDiag = BitSetFactory.createBitSet(64);
   }

   private BitSetUnifier setupBitset(Square... squares) {
      BitSetUnifier tmp = BitSetFactory.createBitSet(64);
      for (Square sq : squares) {
         tmp.set(sq.index());
      }
      return tmp;
   }

   @Test
   public void simplePin() {
      Position p = new Position(Square.e2, Square.g8);
      // d2 pawn is pinned
      p.addPiece(Colour.WHITE, Piece.PAWN, Square.d2);
      p.addPiece(Colour.BLACK, Piece.ROOK, Square.b2);
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.e2.index(), Colour.WHITE);
      assertTrue(pinMaskDiag.isEmpty());
      assertEquals(setupBitset(Square.b2, Square.c2, Square.d2), pinMaskHV);
   }

   @Test
   public void noPinSinceNoEnemyPieceOnRay() {
      Position p = new Position(Square.e2, Square.g8);
      p.addPiece(Colour.WHITE, Piece.PAWN, Square.d2);
      p.addPiece(Colour.WHITE, Piece.ROOK, Square.c2);
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.e2.index(), Colour.WHITE);
      assertTrue(pinMaskDiag.isEmpty());
      assertTrue(pinMaskHV.isEmpty());
   }

   @Test
   public void noPinSinceTwoFriendlyPiecesOnRay() {
      Position p = new Position(Square.e2, Square.g8);
      p.addPiece(Colour.WHITE, Piece.PAWN, Square.d2);
      p.addPiece(Colour.WHITE, Piece.ROOK, Square.c2);
      p.addPiece(Colour.BLACK, Piece.ROOK, Square.a2);
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.e2.index(), Colour.WHITE);
      assertTrue(pinMaskDiag.isEmpty());
      assertTrue(pinMaskHV.isEmpty());
   }

   @Test
   public void noPinSinceBishopDoesNotPinVertically() {
      Position p = new Position(Square.e2, Square.g8);
      p.addPiece(Colour.WHITE, Piece.ROOK, Square.e4);
      p.addPiece(Colour.BLACK, Piece.BISHOP, Square.e7);
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.e2.index(), Colour.WHITE);
      assertTrue(pinMaskDiag.isEmpty());
      assertTrue(pinMaskHV.isEmpty());
   }

   @Test
   public void pinnedPieceCanMoveAlongRay() {
      // Position p = new Position(Square.e2, Square.h8);
      // // d3 bishop is pinned apart from NW/SE ray
      // p.addPiece(Colour.WHITE, Piece.BISHOP, Square.d3);
      // p.addPiece(Colour.WHITE, Piece.PAWN, Square.b5); // pawn is not pinned
      // p.addPiece(Colour.BLACK, Piece.QUEEN, Square.c4);
      // p.addPiece(Colour.BLACK, Piece.BISHOP, Square.a6);
      // TestUtil.checkMoves(p, new MoveGenerator().findMoves(p, Colour.WHITE), "Ke2-d1", "Ke2-d2", "Ke2-e1", "Ke2-e3", "Ke2-f1", "Ke2-f2",
      // "Ke2-f3", "b5-b6",
      // "b5xa6", "Bd3xc4");
   }

   @Test
   public void pinnedPieceCanMoveAlongOppositeRay() {
      // a 'pinned' piece along ray N can still move in direction N or in direction S
      // but in this case not along ray W or E
      Position p = new Position(Square.e2, Square.g8);
      p.addPiece(Colour.WHITE, Piece.ROOK, Square.e4);
      p.addPiece(Colour.BLACK, Piece.ROOK, Square.e7);
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.e2.index(), Colour.WHITE);
      assertTrue(pinMaskDiag.isEmpty());
      assertEquals(setupBitset(Square.e3, Square.e4, Square.e5, Square.e6, Square.e7), pinMaskHV);
   }

   @Test
   public void blockCheck() {
      Position p = Fen.decode("3r4/4k3/8/R7/4P3/3K4/1BN1P3/8 w - - 10 10").getPosition();
      assertTrue(p.isKingInCheck());
      assertEquals(1, p.getCheckSquares().size());
      assertTrue(TestUtil.squareIsCheckSquare(Square.d8, p.getCheckSquares()));
      TestUtil.checkMoves(p, new MoveGenerator().findMoves(p, Colour.WHITE), "Kd3-c3", "Kd3-c4", "Kd3-e3", "Bb2-d4", "Nc2-d4", "Ra5-d5");
   }

   @Test
   public void pinnedQueen() {
      Position p = Fen.decode("5K2/4Q3/8/2b1pQ2/8/8/k4r2/8 w - - 0 0").getPosition();
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.f8.index(), Colour.WHITE);
      assertEquals(setupBitset(Square.e7, Square.d6, Square.c5), pinMaskDiag);
      assertEquals(setupBitset(Square.f7, Square.f6, Square.f5, Square.f4, Square.f3, Square.f2), pinMaskHV);
   }

   @Test
   public void pinsFromAllSide() {
      Position p = Fen.decode("3r4/4k3/b7/1R1Q1b2/4P3/q1PKR2r/1BNNP3/1b1r1qq1 w - - 10 10").getPosition();
      p.createPinInfo(pinMaskHV, pinMaskDiag, Square.d3.index(), Colour.WHITE);
      assertEquals(setupBitset(Square.a6, Square.b5, Square.c4, Square.b1, Square.c2, Square.e4, Square.f5, Square.f1, Square.e2), pinMaskDiag);
      assertEquals(setupBitset(Square.d1, Square.d2, Square.a3, Square.b3, Square.c3, Square.e3, Square.f3, Square.g3, Square.h3, Square.d8, Square.d7,
            Square.d6, Square.d5, Square.d4), pinMaskHV);
   }

}
