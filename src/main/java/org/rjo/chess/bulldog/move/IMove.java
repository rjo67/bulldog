package org.rjo.chess.bulldog.move;

import java.util.ArrayList;
import java.util.List;

import org.rjo.chess.bulldog.game.Position.PieceSquareInfo;

public interface IMove {

   default boolean isCheck() { return false; }

   default List<PieceSquareInfo> getCheckSquares() { return new ArrayList<>(); }

   int getOrigin();

   int getTarget();

   boolean isCapture();

   boolean isEnpassant();

   boolean isPromotion();

   byte getPromotedPiece();

   boolean isKingssideCastling();

   boolean isQueenssideCastling();

   int getSquareOfPawnCapturedEnpassant();

   boolean isPawnTwoSquaresForward();

   public boolean moveCapturesPiece(int captureSquare);

   public String toUCIString();
}
