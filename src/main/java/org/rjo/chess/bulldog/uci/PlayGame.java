package org.rjo.chess.bulldog.uci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.rjo.chess.bulldog.board.Board.Square;
import org.rjo.chess.bulldog.eval.AlphaBeta3;
import org.rjo.chess.bulldog.eval.MoveInfo;
import org.rjo.chess.bulldog.eval.SearchStrategy;
import org.rjo.chess.bulldog.game.Game;
import org.rjo.chess.bulldog.move.IMove;
import org.rjo.chess.bulldog.move.Move;
import org.rjo.chess.bulldog.piece.Piece;
import org.rjo.chess.bulldog.piece.Pieces;

public class PlayGame {

   public static void main(String[] args) throws IOException {
      PlayGame p = new PlayGame();
      p.run();
   }

   private void run() throws IOException {
      Game game = new Game();
      SearchStrategy strategy = new AlphaBeta3(System.out);
      System.out.println("Starting new game with strategy: " + strategy.toString());
      try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
         boolean finished = false;
         while (!finished) {
            System.out.print(game.getMoveNumber() + ":  ");
            try {
               IMove humanMove = getMove(game, in);
               checkValidity(humanMove, game);
               game.getPosition().move(humanMove);
               MoveInfo mi = strategy.findMove(game.getPosition());
               if (mi.getMove() == null) {
                  finished = true;
                  if (game.getPosition().isKingInCheck()) {
                     System.out.println("checkmate!");
                  } else {
                     System.out.println("stalemate!");
                  }
               } else {
                  System.out.println(game.getMoveNumber() + "... " + mi.getMove());
                  game.getPosition().move(mi.getMove());
               }
            } catch (IllegalArgumentException x) {
               System.out.println(x.getMessage());
            }
         }
      }
   }

   private void checkValidity(IMove move, Game game) throws IllegalArgumentException {
      // piece at given square?
      byte piece = game.getPosition().pieceAt(move.getOrigin());
      if (piece == 0) { throw new IllegalArgumentException("No piece at origin square " + move); }
      var originColour = Pieces.colourOf(piece);
      if (originColour != game.getPosition().getSideToMove()) { throw new IllegalArgumentException("Piece at origin square " + move + " is wrong colour"); }

      if (!move.isCapture()) {
         if (game.getPosition().pieceAt(move.getTarget()) != 0) {
            throw new IllegalArgumentException("not a capture move, but piece at target square " + move);
         }
      } else {
         byte targetPiece = game.getPosition().pieceAt(move.getTarget());
         if (targetPiece == 0) { throw new IllegalArgumentException("No piece at target square " + move); }
         var targetColour = Pieces.colourOf(piece);
         // if capture: opponent's piece at given square?
         if (targetColour != game.getPosition().getSideToMove().opposite()) {
            throw new IllegalArgumentException("no opponent's piece at target square " + move);
         }
      }
      // legal move for piece?
      // king in check after move?
   }

   private IMove getMove(Game game, BufferedReader in) throws IllegalArgumentException, IOException {
      IMove m;
      String moveStr = in.readLine();
      if ("O-O".equals(moveStr)) {
         m = Move.KINGS_CASTLING_MOVE[game.getPosition().getSideToMove().ordinal()];
      } else if ("O-O-O".equals(moveStr)) {
         m = Move.QUEENS_CASTLING_MOVE[game.getPosition().getSideToMove().ordinal()];
      } else {
         Piece pt = Piece.convertStringToPieceType(moveStr.charAt(0));
         int reqdStrLen = 6;
         int startOfFromSquare = 1;
         if (pt == Piece.PAWN) {
            startOfFromSquare = 0;
            reqdStrLen = 5;
         }
         if (moveStr.length() < reqdStrLen) {
            if (pt == Piece.PAWN) {
               throw new IllegalArgumentException("invalid input. Must be >=5 chars for a pawn move");
            } else {
               throw new IllegalArgumentException("invalid input. Must be >=6 chars");
            }
         }
         if (!(moveStr.charAt(startOfFromSquare + 2) == 'x' || moveStr.charAt(startOfFromSquare + 2) == '-')) {
            throw new IllegalArgumentException("invalid input. Expected 'x' or '-' at position " + (startOfFromSquare + 3));
         }
         Square from = Square.fromString(moveStr.substring(startOfFromSquare, startOfFromSquare + 2));
         byte pieceAtFromSquare = game.getPosition().pieceAt(from.index());
         var colour = Pieces.colourOf(pieceAtFromSquare);
         var pieceType = Pieces.toPiece(pieceAtFromSquare);
         if (pieceType != pt || colour != game.getPosition().getSideToMove()) { throw new IllegalArgumentException("error: no " + pt + " at square " + from); }
         boolean capture = moveStr.charAt(startOfFromSquare + 2) == 'x';
         Square to = Square.fromString(moveStr.substring(startOfFromSquare + 3, startOfFromSquare + 5));
         if (capture) {
            m = Move.createCapture(from.index(), to.index(), (byte) 0);
         } else {
            m = Move.createMove(from.index(), to.index());
         }
         if (pt == Piece.PAWN && to.rank() == 7) {
            System.out.println("promote to? ");
            String promote = in.readLine();
            if (promote==null || promote.length() != 1) { throw new IllegalArgumentException("promote piece must be 1 char"); }
            Piece promotedPiece = Piece.convertStringToPieceType(promote.charAt(0));
            if (promotedPiece == Piece.PAWN || promotedPiece == Piece.KING) { throw new IllegalArgumentException("cannot promote to a pawn or a king"); }
            m = Move.createPromotionMove(from.index(), to.index(), Pieces.fromPiece(promotedPiece, game.getPosition().getSideToMove()));
         }
      }

      return m;
   }

}
