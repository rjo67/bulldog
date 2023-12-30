package org.rjo.chess.bulldog.uci;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.rjo.chess.bulldog.board.Board.Square;
import org.rjo.chess.bulldog.eval.AlphaBeta3;
import org.rjo.chess.bulldog.eval.MoveInfo;
import org.rjo.chess.bulldog.eval.SearchStrategy;
import org.rjo.chess.bulldog.game.Fen;
import org.rjo.chess.bulldog.game.Game;
import org.rjo.chess.bulldog.game.Position.PieceSquareInfo;
import org.rjo.chess.bulldog.move.CheckMoveDecorator;
import org.rjo.chess.bulldog.move.IMove;
import org.rjo.chess.bulldog.move.Move;
import org.rjo.chess.bulldog.piece.Colour;
import org.rjo.chess.bulldog.piece.Piece;
import org.rjo.chess.bulldog.piece.Pieces;

/**
 * Starts threads for a UCI-conform interface and the engine.
 *
 * @author rich
 */
public class UCI {
   private Game game;

   private MoveInfo moveinfo;

   // set after "processCommandPosition" to store the last move from the uci string
   private IMove lastMove;

   public static void main(String[] args) {
      UCI uci = new UCI();
      uci.run();
   }

   /**
    * interface for tests.
    *
    * @param cmd the required command
    * @return the UCI object
    */
   public static UCI testInterface(String cmd) {
      UCI uci = new UCI();

      try (Scanner lineScanner = new Scanner(cmd)) {
         uci.processLine(lineScanner);
      }
      return uci;
   }

   public void run() {
      boolean finished = false;
      try (Scanner sc = new Scanner(System.in)) {
         while (!finished) {
            String line = sc.nextLine();
            try (Scanner lineScanner = new Scanner(line)) {
               finished = processLine(lineScanner);
            }
         }
      }
   }

   /* for tests */
   public Game getGame() { return game; }

   /**
    * process one uci command line.
    *
    * @param lineScanner
    * @return true if processing is finished (e.g. "quit" received)
    */
   boolean processLine(Scanner lineScanner) {
      boolean finished = false;
      if (lineScanner.hasNext()) {
         String nextCmd = lineScanner.next();
         switch (nextCmd) {
         case "uci":
            processCommandUci();
            break;
         case "isready":
            processCommandIsReady();
            break;
         case "go":
            processCommandGo(lineScanner);
            break;
         case "stop":
            processCommandStop();
            break;
         case "position":
            processCommandPosition(lineScanner);
            break;
         case "quit":
            finished = true;
            break;
         default:
            System.out.println("unrecognised: " + nextCmd);
         }
      } else {
         finished = true;
      }
      return finished;
   }

   private void processCommandIsReady() {
      System.out.println("readyok");
   }

   private void processCommandGo(Scanner lineScanner) {
      boolean infinite = lineScanner.hasNext() && "infinite".equals(lineScanner.next());

      SearchStrategy strategy = new AlphaBeta3(System.out /* , game.getZobristMap() */);

      // UCI reporter thread
      UciReporter uciReporter = new UciReporter(strategy, System.out);
      new Thread(uciReporter).start();

      moveinfo = _findMove(strategy);

      uciReporter.setStop(true);
      if (!infinite) { System.out.println("bestmove " + moveinfo.getMove().toUCIString()); }
   }

   /** for tests */
   public MoveInfo findMove(SearchStrategy strategy) {
      return _findMove(strategy);
   }

   private MoveInfo _findMove(SearchStrategy strategy) {

      // Map<PieceType, Integer>[] pieceCounts = analysePosition(game.getPosition());
      //
      // int nbrPieces = 0;
      // for (Colour colour : Colour.ALL_COLOURS) {
      // nbrPieces += pieceCounts[colour.ordinal()].values().stream().mapToInt(Integer::intValue).sum();
      // }
      // // simplistic way to increase the search depth if fewer pieces on the board
      // strategy.incrementDepth((32 - nbrPieces) / 12);
      // System.out.println("set strategy depth to " + strategy.getCurrentDepth());
      //
      // // King.IN_ENDGAME = inEndgame(pieceCounts);

      // can clear the zobrist map if the last move was a pawn move
      // if (lastMove.getPiece() == PieceType.PAWN) {
      // // game.getZobristMap().clear();
      // }

      return strategy.findMove(game.getPosition());
   }

   // private Map<PieceType, Integer>[] analysePosition(Position position) {
   // @SuppressWarnings("unchecked")
   // Map<PieceType, Integer>[] pieceCounts = new HashMap[Colour.ALL_COLOURS.length];
   // for (Colour colour : Colour.ALL_COLOURS) {
   // final Piece[] pieces = position.getPieces(colour);
   // Map<PieceType, Integer> counts = new HashMap<>();
   // counts.put(PieceType.KING, 1); // always one king ;-)
   // for (PieceType pieceType : PieceType.ALL_PIECE_TYPES_EXCEPT_KING) {
   // counts.put(pieceType, pieces[pieceType.ordinal()].numberOfPieces());
   // }
   // pieceCounts[colour.ordinal()] = counts;
   // }
   // return pieceCounts;
   // }

   /**
    * Are we in an endgame?
    *
    * @param pieceCounts counts of pieces still present
    * @return true if considerered to be in an endgame
    */
   private boolean inEndgame(Map<Piece, Integer>[] pieceCounts) {
      // in endgame if each side has <= 13 material points
      boolean inEndgame = true;
      for (Colour colour : Colour.ALL_COLOURS) {
         int points = (9 * pieceCounts[colour.ordinal()].get(Piece.QUEEN)) + (5 * pieceCounts[colour.ordinal()].get(Piece.ROOK))
               + (3 * pieceCounts[colour.ordinal()].get(Piece.BISHOP)) + (3 * pieceCounts[colour.ordinal()].get(Piece.KNIGHT))
               + pieceCounts[colour.ordinal()].get(Piece.PAWN);
         inEndgame = inEndgame && (points <= 13);
      }
      return inEndgame;
   }

   private void processCommandStop() {
      if (moveinfo != null) { System.out.println("bestmove " + moveinfo.getMove().toUCIString()); }
   }

   private void processCommandPosition(Scanner lineScanner) {
      // position [fen <fenstring> | startpos ] moves <move1> .... <movei>
      this.lastMove = null;
      String subcmd = lineScanner.next();
      if (!"moves".equals(subcmd)) {
         String fen = null;
         if ("fen".equals(subcmd)) {
            fen = lineScanner.next();
         } else if ("startpos".equals(subcmd)) {
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w kqKQ - 0 1";
         } else {
            throw new RuntimeException("invalid value after 'position': expected fen or startpos, got '" + subcmd + "'");
         }
         game = Fen.decode(fen);

         // move on to "moves"
         if (lineScanner.hasNext()) { subcmd = lineScanner.next(); }
      }
      if ("moves".equals(subcmd)) {
         // process moves
         while (lineScanner.hasNext()) {
            String moveStr = lineScanner.next();
            boolean lastmove = !lineScanner.hasNext();
            IMove m = fromUCIString(moveStr, game);
            // only worry about check for the last move
            game.makeMove(m);
            if (lastmove) {
               this.lastMove = m;
               Square kingsSquare = Square.toSquare(game.getPosition().getKingsSquare(game.getPosition().getSideToMove()));

               List<PieceSquareInfo> checkSquares = game.getPosition().isKingInCheck(kingsSquare.index(), game.getPosition().getSideToMove().opposite());
               if (!checkSquares.isEmpty()) {
                  m = new CheckMoveDecorator(m, checkSquares);
                  game.getPosition().setKingInCheck();
               }
               System.out.println("after move " + m + ", fen:" + Fen.encode(game));
            }
         }
      }
   }

   /**
    * converts from uci style move to a move object
    *
    * @param moveStr uci move e.g. b7d5, c2c1q
    * @return move object. Whether 'Check' is not examined!
    */
   public static IMove fromUCIString(String moveStr, final Game game) {
      Square from = Square.fromString(moveStr.substring(0, 2));
      Square to = Square.fromString(moveStr.substring(2, 4));
      byte piece = game.getPosition().pieceAt(from.index());
      var pieceType = Pieces.toPiece(piece);
      var colour = Pieces.colourOf(piece);
      if (colour != game.getPosition().getSideToMove()) { throw new IllegalArgumentException("Piece at target square " + to + " is wrong colour"); }
      IMove m;
      // check for promotion
      if (moveStr.length() == 5) {
         Piece promotedPiece = Piece.convertStringToPieceType(moveStr.charAt(4));
         m = Move.createPromotionMove(from.index(), to.index(), Pieces.generatePiece(promotedPiece, game.getPosition().getSideToMove()));
         // check move is valid
         int validRank = game.getPosition().getSideToMove() == Colour.WHITE ? 7 : 0;
         if (pieceType != Piece.PAWN || to.rank() != validRank) {
            throw new IllegalArgumentException("UCI string " + moveStr + " indicates pawn promotion, but incorrect piece/rank");
         }
      } else {
         boolean kingsMove = pieceType == Piece.KING;
         if (kingsMove && from == Square.e1 && to == Square.g1) {
            m = Move.KINGS_CASTLING_MOVE[Colour.WHITE.ordinal()];
         } else if (kingsMove && from == Square.e8 && to == Square.g8) {
            m = Move.KINGS_CASTLING_MOVE[Colour.BLACK.ordinal()];
         } else if (kingsMove && from == Square.e1 && to == Square.c1) {
            m = Move.QUEENS_CASTLING_MOVE[Colour.WHITE.ordinal()];
         } else if (kingsMove && from == Square.e8 && to == Square.c8) {
            m = Move.QUEENS_CASTLING_MOVE[Colour.BLACK.ordinal()];
         } else {
            byte targetPiece = game.getPosition().pieceAt(to.index());
            if (targetPiece == 0) {
               // not a capture -- unless enpassant
               Square enpassantSquare = game.getPosition().getEnpassantSquare();
               if (enpassantSquare != null && pieceType == Piece.PAWN && to == enpassantSquare) {
                  m = Move.createEnpassantMove(from.index(), enpassantSquare.index(), game.getPosition().getSideToMove());
               } else {
                  m = Move.createMove(from.index(), to.index());
               }
            } else {
               var targetColour = Pieces.colourOf(targetPiece);
               if (targetColour != game.getPosition().getSideToMove().opposite()) {
                  throw new IllegalArgumentException("Piece at target square " + to + " is wrong colour");
               }
               m = Move.createCapture(from.index(), to.index(), (byte) 0);
            }
         }
      }
      return m;

   }

   private void processCommandUci() {
      System.out.println("id name bulldog 2.0");
      System.out.println("id author rjo67");
      System.out.println("uciok");
   }

   static class NullOutputStream extends OutputStream {
      @Override
      public void write(@SuppressWarnings("unused") int arg0) {
      }
   }

}
