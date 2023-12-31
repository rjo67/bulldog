package org.rjo.chess.bulldog.move;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.IntFunction;

import org.rjo.chess.bulldog.bits.BitSetFactory;
import org.rjo.chess.bulldog.bits.BitSetHelper;
import org.rjo.chess.bulldog.bits.BitSetUnifier;
import org.rjo.chess.bulldog.board.Board;
import org.rjo.chess.bulldog.board.Ray;
import org.rjo.chess.bulldog.board.Board.Square;
import org.rjo.chess.bulldog.game.Position;
import org.rjo.chess.bulldog.game.Position.PieceSquareInfo;
import org.rjo.chess.bulldog.move.MoveGenerator.RayCacheInfo.RayCacheState;
import org.rjo.chess.bulldog.move.MoveGenerator.SquareInfo.State;
import org.rjo.chess.bulldog.piece.Colour;
import org.rjo.chess.bulldog.piece.Piece;
import org.rjo.chess.bulldog.piece.Pieces;

/**
 * @author rich
 *
 */
public class MoveGenerator implements MoveGeneratorI {

   private boolean verbose;

   /**
    * A MoveNode is an entry in a linked list which stores information about the possible next moves.
    * 
    * Because a move to a particular square could be a capture, both types of move are stored in the MoveNode instance.
    * 
    * The elements in the 'next' array are links to the next MoveNodes. In general, the linked list should be followed (node[0]) until the next
    * target square is found to be occupied, which potentially generates a capture move. Then next[1] should be used.
    * 
    * <ul>
    * <li>For sliding pieces: next[0] gives the next move along the ray. If null, then next[1] gives the first move in the next direction.</li>
    * <li>For pawns: 'move' represents the one-sq-forward move, and next[0] is two-sqs-forward. (next[1] is null)</li>
    * <li>For knights: next[0] represents the next move. next[1] points to the same node</li>
    * </ul>
    */
   public static class MoveNode {
      // either specify 'to', or give the 'move'/'capture'
      private final int to; // TODO candidate to be removed.... is also stored in move.getTarget() or captureMove.getTarget()
      private final Square toSq;
      private final IMove move;
      private final IMove captureMove;
      private final MoveNode[] next = new MoveNode[2];

      MoveNode(int to) {
         this(null, null, to);
      }

      MoveNode(IMove move, IMove capture, int to) {
         this.to = to;
         this.toSq = Square.toSquare(to);
         this.move = move;
         this.captureMove = capture;
      }

      void addNext(MoveNode next) {
         this.next[0] = next;
      }

      void addNextDirection(MoveNode next) {
         this.next[1] = next;
      }

      public int getTo() { return to; }

      public IMove getMove() { return move; }

      public IMove getCaptureMove() { return captureMove; }

      public MoveNode[] getNext() { return next; }

      @Override
      public String toString() {
         return "(to=" + toSq + //
               (move != null ? ", m=" + move.toString() : (captureMove != null ? ", c=" + captureMove.toString() : "null")) + //
               ", n=" + next[0] + ", nd=" + next[1] + ")";
      }
   }

   /** Stores info about enpassant squares */
   private static record EnpassantInfo(int squareOfPawnToBeTakenEnpassant, IMove enpassantMove) {
      // empty
   }

   // **** Note
   // the first dimension of all these arrays is indexed on colour (w/b)
   // ****

   /** square where the king must be to be able to castle */
   public final static int[] kingsCastlingSquareIndex = new int[] { Square.e1.index(), Square.e8.index() };
   /** square where the king ends up after castling kings or queensside, indexed on colour */
   public final static int[][] kingsSquareAfterCastling = new int[][] { { Square.g1.index(), Square.c1.index() }, { Square.g8.index(), Square.c8.index() } };
   /** square where the rook ends up after castling kings or queensside, indexed on colour and side of board */
   public final static int[][] rooksSquareAfterCastling = new int[][] { { Square.f1.index(), Square.d1.index() }, { Square.f8.index(), Square.d8.index() } };
   /** stores the rook's squares for kingsside or queensside castling, indexed on colour and side of board */
   public final static int[][] rooksCastlingSquareIndex = new int[][] { { Square.h1.index(), Square.a1.index() }, { Square.h8.index(), Square.a8.index() } };
   /** squares which must be unoccupied in order to castle kingsside */
   private final static int[][] unoccupiedSquaresKingssideCastling = new int[][]//
   { { Square.f1.index(), Square.g1.index() }, { Square.f8.index(), Square.g8.index() } };
   /** if an enemy knight is on these squares, then cannot castle kingsside */
   private final static int[][] knightSquaresKingssideCastling = new int[][]//
   { { Square.d2.index(), Square.e3.index(), Square.g3.index(), Square.h2.index(), Square.e2.index(), Square.f3.index(), Square.h3.index() },
         { Square.d7.index(), Square.e6.index(), Square.g6.index(), Square.h7.index(), Square.e7.index(), Square.f6.index(), Square.h6.index() } };
   /** squares which must be unoccupied in order to castle queensside */
   private final static int[][] unoccupiedSquaresQueenssideCastling = new int[][]//
   { { Square.b1.index(), Square.c1.index(), Square.d1.index() }, { Square.b8.index(), Square.c8.index(), Square.d8.index() } };
   /**
    * if an enemy knight is on these squares, then cannot castle queensside (not including c2/c7, since a knight on that square checks the
    * king)
    */
   private final static int[][] knightSquaresQueenssideCastling = new int[][]//
   { { Square.b3.index(), Square.c3.index(), Square.d3.index(), Square.e3.index(), Square.a2.index(), Square.b2.index(), Square.e2.index(), Square.f2.index() },
         { Square.b6.index(), Square.c6.index(), Square.d6.index(), Square.e6.index(), Square.a7.index(), Square.b7.index(), Square.e7.index(),
               Square.f7.index() } };
   /** Stores (for both colours) the squares (dim1) where a pawn must be in order to take a pawn on dim0 with e.p. */
   private final static EnpassantInfo[][] enpassantSquares = new EnpassantInfo[64][];
   /** Stores set of possible knight moves for each square) */
   public final static Set<Integer>[] knightMoves;

   public final static int[][][] pawnCaptures = new int[2][64][]; // dim0: w/b; dim1: squares; dim2: possible pawn captures (max 2)
   public final static MoveNode[][] pawnCaptureNodes = new MoveNode[2][64]; // dim0: w/b; dim1: head of a linked list of possible captures

   public final static MoveNode[][] pawnMoves = new MoveNode[2][64]; // dim0: w/b; dim1: head of a linked list of possible moves

   /** holds linked lists of moves for various pieces (e.g. sliding pieces, knights, king). Pawns are treated separately */
   /* package */ final static MoveNode[][] moveNodes = new MoveNode[6][64]; // dim0=piece type; dim1=head of a linked list of possible squares to move to

   /**
    * Valid squares for the king to move to, stored as bitsets for each square.
    */
   private static final BitSetUnifier[] KING_MOVES = new BitSetUnifier[64];

   /**
    * Valid squares for the knight to move to, stored as bitsets for each square.
    */
   private static final BitSetUnifier[] KNIGHT_MOVES = new BitSetUnifier[64];

   static {
      enpassantSquares[Square.a6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.b5.index(), Move.createEnpassantMove(Square.b5.index(), Square.a6.index(), Colour.WHITE)) };
      enpassantSquares[Square.b6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.a5.index(), Move.createEnpassantMove(Square.a5.index(), Square.b6.index(), Colour.WHITE)),
            new EnpassantInfo(Square.c5.index(), Move.createEnpassantMove(Square.c5.index(), Square.b6.index(), Colour.WHITE)) };
      enpassantSquares[Square.c6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.b5.index(), Move.createEnpassantMove(Square.b5.index(), Square.c6.index(), Colour.WHITE)),
            new EnpassantInfo(Square.d5.index(), Move.createEnpassantMove(Square.d5.index(), Square.c6.index(), Colour.WHITE)) };
      enpassantSquares[Square.d6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.c5.index(), Move.createEnpassantMove(Square.c5.index(), Square.d6.index(), Colour.WHITE)),
            new EnpassantInfo(Square.e5.index(), Move.createEnpassantMove(Square.e5.index(), Square.d6.index(), Colour.WHITE)) };
      enpassantSquares[Square.e6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.d5.index(), Move.createEnpassantMove(Square.d5.index(), Square.e6.index(), Colour.WHITE)),
            new EnpassantInfo(Square.f5.index(), Move.createEnpassantMove(Square.f5.index(), Square.e6.index(), Colour.WHITE)) };
      enpassantSquares[Square.f6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.e5.index(), Move.createEnpassantMove(Square.e5.index(), Square.f6.index(), Colour.WHITE)),
            new EnpassantInfo(Square.g5.index(), Move.createEnpassantMove(Square.g5.index(), Square.f6.index(), Colour.WHITE)) };
      enpassantSquares[Square.g6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.f5.index(), Move.createEnpassantMove(Square.f5.index(), Square.g6.index(), Colour.WHITE)),
            new EnpassantInfo(Square.h5.index(), Move.createEnpassantMove(Square.h5.index(), Square.g6.index(), Colour.WHITE)) };
      enpassantSquares[Square.h6.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.g5.index(), Move.createEnpassantMove(Square.g5.index(), Square.h6.index(), Colour.WHITE)) };

      enpassantSquares[Square.a3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.b4.index(), Move.createEnpassantMove(Square.b4.index(), Square.a3.index(), Colour.BLACK)) };
      enpassantSquares[Square.b3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.a4.index(), Move.createEnpassantMove(Square.a4.index(), Square.b3.index(), Colour.BLACK)),
            new EnpassantInfo(Square.c4.index(), Move.createEnpassantMove(Square.c4.index(), Square.b3.index(), Colour.BLACK)) };
      enpassantSquares[Square.c3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.b4.index(), Move.createEnpassantMove(Square.b4.index(), Square.c3.index(), Colour.BLACK)),
            new EnpassantInfo(Square.d4.index(), Move.createEnpassantMove(Square.d4.index(), Square.c3.index(), Colour.BLACK)) };
      enpassantSquares[Square.d3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.c4.index(), Move.createEnpassantMove(Square.c4.index(), Square.d3.index(), Colour.BLACK)),
            new EnpassantInfo(Square.e4.index(), Move.createEnpassantMove(Square.e4.index(), Square.d3.index(), Colour.BLACK)) };
      enpassantSquares[Square.e3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.d4.index(), Move.createEnpassantMove(Square.d4.index(), Square.e3.index(), Colour.BLACK)),
            new EnpassantInfo(Square.f4.index(), Move.createEnpassantMove(Square.f4.index(), Square.e3.index(), Colour.BLACK)) };
      enpassantSquares[Square.f3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.e4.index(), Move.createEnpassantMove(Square.e4.index(), Square.f3.index(), Colour.BLACK)),
            new EnpassantInfo(Square.g4.index(), Move.createEnpassantMove(Square.g4.index(), Square.f3.index(), Colour.BLACK)) };
      enpassantSquares[Square.g3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.f4.index(), Move.createEnpassantMove(Square.f4.index(), Square.g3.index(), Colour.BLACK)),
            new EnpassantInfo(Square.h4.index(), Move.createEnpassantMove(Square.h4.index(), Square.g3.index(), Colour.BLACK)) };
      enpassantSquares[Square.h3.index()] = new EnpassantInfo[] {
            new EnpassantInfo(Square.g4.index(), Move.createEnpassantMove(Square.g4.index(), Square.h3.index(), Colour.BLACK)) };

      // pawn moves: first MoveNode stores 'one sq forward', linking to 2nd node '2 squares forward' if applicable
      for (Colour col : new Colour[] { Colour.WHITE, Colour.BLACK }) {
         for (int startSq = 0; startSq < 64; startSq++) {
            int[] targetSquares = col == Colour.WHITE ? Ray.raysList[startSq][Ray.NORTH.ordinal()] : Ray.raysList[startSq][Ray.SOUTH.ordinal()];
            // ignore pawn on first rank for white / 8th rank for black (ray is empty) which can't happen anyway
            if (targetSquares.length != 0) {
               if (Square.toSquare(targetSquares[0]).onLastRank(col)) {
                  // promotion moves
                  MoveNode current = null;
                  for (Piece pt : new Piece[] { Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN }) {
                     MoveNode promotion = new MoveNode(Move.createPromotionMove(startSq, targetSquares[0], Pieces.generatePiece(pt, col)), null,
                           targetSquares[0]);
                     if (current == null) {
                        pawnMoves[col.ordinal()][startSq] = promotion;
                     } else {
                        current.addNext(promotion);
                     }
                     current = promotion;
                  }
               } else {
                  MoveNode oneSqForwardMove = new MoveNode(new Move(startSq, targetSquares[0], (byte) 0, (byte) 0), null, targetSquares[0]);
                  pawnMoves[col.ordinal()][startSq] = oneSqForwardMove;
                  if (Square.toSquare(startSq).onPawnStartRank(col)) {
                     MoveNode twoSqForwardMove = new MoveNode(new Move(startSq, targetSquares[1], false, (byte) 0, false, 0, false, false, true), null,
                           targetSquares[1]);
                     oneSqForwardMove.addNext(twoSqForwardMove);
                  }
               }
            }
         }
      }

      int[][] captureOffset = new int[][] { { -9, -11 }, { 9, 11 } };
      for (Colour col : new Colour[] { Colour.WHITE, Colour.BLACK }) {
         // process first and last rank as well, need these squares defined for kingIsInCheckAfterMove()
         for (int sq = 0; sq < 64; sq++) {
            var tmpPawnCaptures = new ArrayList<Integer>();
            for (int offset : captureOffset[col.ordinal()]) {
               int targetSq = Board.getMailboxSquare(sq, offset);
               if (targetSq != -1) { tmpPawnCaptures.add(targetSq); }
            }
            pawnCaptures[col.ordinal()][sq] = new int[tmpPawnCaptures.size()];
            var slot = 0;
            for (int i : tmpPawnCaptures) {
               pawnCaptures[col.ordinal()][sq][slot] = i;
               slot++;
            }
         }
      }
      // pawn captures: first MoveNode stores 'capture right' (potentially multiple promotion captures), 'nextDir' links to 'capture left'
      for (Colour col : new Colour[] { Colour.WHITE, Colour.BLACK }) {
         for (int startSq = 0; startSq < 64; startSq++) {
            // pawnCaptures defines moves for pawns on first/last rank -- ignore these for this data structure
            if (!Square.toSquare(startSq).onFirstRank(col)) {
               int[] targetSquares = pawnCaptures[col.ordinal()][startSq];
               if (targetSquares.length != 0) {
                  if (Square.toSquare(targetSquares[0]).onLastRank(col)) {
                     // promotion captures
                     // next[0] links the moves together, to be safe all should set next[1]
                     List<MoveNode> allPromotionMoves = new ArrayList<>();
                     MoveNode current = null;
                     for (Piece pt : new Piece[] { Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN }) {
                        MoveNode promotionCapture = new MoveNode(null, new Move(startSq, targetSquares[0], (byte) 1, Pieces.generatePiece(pt, col)),
                              targetSquares[0]);
                        if (current == null) {
                           pawnCaptureNodes[col.ordinal()][startSq] = promotionCapture;
                        } else {
                           current.addNext(promotionCapture);
                        }
                        current = promotionCapture;
                        allPromotionMoves.add(current);
                     }
                     current = null; // reset for next loop
                     if (targetSquares.length > 1) {
                        // process promotion capture right
                        for (Piece pt : new Piece[] { Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN }) {
                           MoveNode promotionCapture = new MoveNode(null, new Move(startSq, targetSquares[1], (byte) 1, Pieces.generatePiece(pt, col)),
                                 targetSquares[1]);
                           if (current == null) {
                              // set nextDir for all promotion moves to point to this one
                              for (MoveNode node : allPromotionMoves) {
                                 node.addNextDirection(promotionCapture);
                              }
                           } else {
                              current.addNext(promotionCapture);
                           }
                           current = promotionCapture;
                        }
                     }
                  } else {
                     MoveNode captureRight = new MoveNode(null, new Move(startSq, targetSquares[0], (byte) 1, (byte) 0), targetSquares[0]);
                     pawnCaptureNodes[col.ordinal()][startSq] = captureRight;
                     if (targetSquares.length > 1) {
                        captureRight.addNextDirection(new MoveNode(null, new Move(startSq, targetSquares[1], (byte) 1, (byte) 0), targetSquares[1]));
                     }
                  }
               }
            }
         }
      }

      for (Piece piece : new Piece[] { Piece.ROOK, Piece.BISHOP, Piece.QUEEN, Piece.KING }) {
         Ray[] raysToCheck;
         switch (piece) {
         case ROOK:
            raysToCheck = new Ray[] { Ray.NORTH, Ray.EAST, Ray.SOUTH, Ray.WEST };
            break;
         case BISHOP:
            raysToCheck = Ray.RAY_TYPES_DIAGONAL;
            break;
         case QUEEN, KING:
            raysToCheck = new Ray[] { Ray.NORTH, Ray.NORTHEAST, Ray.EAST, Ray.SOUTHEAST, Ray.SOUTH, Ray.SOUTHWEST, Ray.WEST, Ray.NORTHWEST };
            break;
         default:
            throw new UnsupportedOperationException("piece " + piece + " not supported");
         }
         for (int fromSq = 0; fromSq < 64; fromSq++) {
            List<MoveNode> newlyCreatedNodes = new ArrayList<>();
            MoveNode prev = null;
            for (Ray ray : raysToCheck) {
               int[] targetSquares = Ray.raysList[fromSq][ray.ordinal()];
               for (int targetSquareIndex = 0; targetSquareIndex < targetSquares.length; targetSquareIndex++) {
                  MoveNode node = new MoveNode(//
                        Move.createMove(fromSq, targetSquares[targetSquareIndex]), //
                        new Move(fromSq, targetSquares[targetSquareIndex], (byte) 1, (byte) 0), // byte(1) indicates a capture
                        targetSquares[targetSquareIndex]);
                  if (prev == null) {
                     moveNodes[piece.ordinal()][fromSq] = node;
                  } else {
                     prev.addNext(node);
                  }
                  prev = node;
                  if (targetSquareIndex == 0 && !newlyCreatedNodes.isEmpty()) {
                     // update nextDir for all nodes created in the previous iteration
                     for (MoveNode n : newlyCreatedNodes) {
                        n.addNextDirection(node);
                     }
                     newlyCreatedNodes.clear();
                  }
                  newlyCreatedNodes.add(node);
                  if (piece == Piece.KING) {
                     break; // only process one square of each ray
                  }
               }
            }
         }
      }

      knightMoves = new Set[64];
      for (int sq = 0; sq < 64; sq++) {
         KNIGHT_MOVES[sq] = BitSetFactory.createBitSet(64);
         knightMoves[sq] = new HashSet<>();
         for (int offset : Piece.KNIGHT.getMoveOffsets()) {
            int targetSq = Board.getMailboxSquare(sq, offset);
            if (targetSq != -1) {
               knightMoves[sq].add(targetSq);
               KNIGHT_MOVES[sq].set(targetSq);
            }
         }
      }

      // the knight moves are also stored in 'moveNodes' so that the algorithm can deal with knights in the same way as other pieces
      Piece piece = Piece.KNIGHT;
      for (int fromSq = 0; fromSq < 64; fromSq++) {
         MoveNode prev = null;
         Integer[] moves = knightMoves[fromSq].toArray(new Integer[0]);
         for (int targetSquareIndex = 0; targetSquareIndex < moves.length; targetSquareIndex++) {
            MoveNode node = new MoveNode(//
                  Move.createMove(fromSq, moves[targetSquareIndex]), //
                  new Move(fromSq, moves[targetSquareIndex], (byte) 1, (byte) 0), // byte(1) indicates a capture
                  moves[targetSquareIndex]);
            if (prev == null) {
               moveNodes[piece.ordinal()][fromSq] = node;
            } else {
               // knight moves are independent of whether target square is occupied; therefore store in both 'next' and 'next direction'.
               // if the target square is empty, 'next' will be used. If occupied, 'nextDirection'.
               prev.addNext(node);
               prev.addNextDirection(node);
            }
            prev = node;
         }
      }

      // 0 == top-left
      for (int i = 0; i < 64; i++) {
         BitSetUnifier myBitSet = BitSetFactory.createBitSet(64);
         myBitSet.set(i);

         /*
          * calculate left and right attack then shift up and down one rank
          */
         BitSetUnifier combined = BitSetHelper.shiftOneWest(myBitSet);
         BitSetUnifier east = BitSetHelper.shiftOneEast(myBitSet);
         combined.or(east);

         // save the current state
         BitSetUnifier possibleMoves = (BitSetUnifier) combined.clone();
         // now add the king's position again and shift up and down one rank
         combined.or(myBitSet);
         BitSetUnifier north = BitSetHelper.shiftOneNorth(combined);
         BitSetUnifier south = BitSetHelper.shiftOneSouth(combined);
         // add to result
         possibleMoves.or(north);
         possibleMoves.or(south);

         KING_MOVES[i] = possibleMoves;
      }
   }

   public MoveGenerator() {
      this(false);
   }

   public MoveGenerator(boolean verbose) {
      this.verbose = verbose;
   }

   @Override
   public List<IMove> findMoves(Position posn, Colour colour) {
      /*
       * Instead of looking at all the squares from 0..63, starts at the kingsSquare and proceeds in ray order first. Then all other squares are
       * processed. This is done to reduce / simplify the amount of work needed later to see if a move left our king in check. The
       * 'squaresProcessed' array is used to keep track of which squares have been processed during the 'rays' loop.
       */
      boolean[] squaresProcessed = new boolean[64]; // false - not processed
      // stores moves in the ray directions. Moves are stored in the direction of the ray, starting next to the kings square
      @SuppressWarnings("unchecked")
      List<IMove>[] movesWithStartSqOnRay = new List[Ray.values().length];
      List<IMove> otherMoves = new ArrayList<>(30); // stores moves from all other squares
      List<IMove> kingMoves = new LinkedList<>(); // stores king moves

      int kingsSquare = posn.getKingsSquare(colour);
      // this bitset stores info about squares which block the check (bit is set).
      BitSetUnifier checkMask;
      // the 'kingsForbiddenSquaresMask' serves as the set of squares the king _cannot_ move to. So starts off empty.
      BitSetUnifier kingsForbiddenSquaresMask = null;
      PieceSquareInfo checkInfo = null;
      var kingInDoubleCheck = posn.isKingInCheck() && posn.getCheckSquares().size() == 2;

      if (posn.isKingInCheck()) {
         checkInfo = posn.getCheckSquares().get(0);
         // this mask will be all '0's if no ray between the squares exists
         long checkMaskLong = Ray.bitmaskBetweenSquares[checkInfo.square()][kingsSquare];
         long kingsForbiddenSquaresMaskLong = checkMaskLong;

         Ray ray = Ray.findRayBetween(kingsSquare, checkInfo.square());
         if (ray != null) {
            checkInfo.setRayToKing(ray);
            /*
             * Because moves are generated along the ray, starting next to the king, we can keep track of the first piece that we find. Any further
             * pieces along this ray cannot be pinned.
             */
         }

         if (kingInDoubleCheck) {
            // extend check mask
            checkMaskLong |= Ray.bitmaskBetweenSquares[posn.getCheckSquares().get(1).square()][kingsSquare];
            kingsForbiddenSquaresMaskLong |= Ray.bitmaskBetweenSquares[posn.getCheckSquares().get(1).square()][kingsSquare];
         }
         checkMask = BitSetFactory.createBitSet(checkMaskLong);
         // *** always add square of checking piece to checkMask
         // (a knight check is not on a ray to the king)
         // this square does not get added to the kingsForbiddenSquaresMask
         checkMask.set(posn.getCheckSquares().get(0).square());
         if (kingInDoubleCheck) { checkMask.set(posn.getCheckSquares().get(1).square()); }
         kingsForbiddenSquaresMask = BitSetFactory.createBitSet(kingsForbiddenSquaresMaskLong);
      } else {
         checkMask = BitSetFactory.createBitSet(Ray.fullysetBsAsLong); // all squares are OK
         kingsForbiddenSquaresMask = BitSetFactory.createBitSet(64); // all squares are OKs
      }
      // remove target squares occupied by my own pieces -- TODO only necesary if not in check (i.e. preceding else?)
      // checkMask.andNot(posn.getPiecesBitset(colour));

      // the king' mask is now expanded to prevent moving adjacent to the opponents king.
      final var opponentsKing = posn.getKingsSquare(posn.getSideToMove().opposite());
      kingsForbiddenSquaresMask.or(KING_MOVES[opponentsKing]);
      generateKingMoves(posn, kingsSquare, colour, kingMoves, kingsForbiddenSquaresMask);
      squaresProcessed[kingsSquare] = true;

      if (!kingInDoubleCheck) {

         // generate all moves: only moves which get us out of check (if necessary) will be recorded

         for (Ray ray : Ray.values()) {
            movesWithStartSqOnRay[ray.ordinal()] = new LinkedList<>();
            for (int raySq : Ray.raysList[kingsSquare][ray.ordinal()]) {
               processSquare(posn, raySq, colour, movesWithStartSqOnRay[ray.ordinal()], checkInfo, kingsSquare, checkMask);
               squaresProcessed[raySq] = true;
            }
         }
         // remove moves along rays to king if the piece is pinned
         removeMovesLeavingKingInCheckAlongRay(posn, posn.getKingsSquare(colour), colour, movesWithStartSqOnRay);

         // process all other squares
         for (int sq = 0; sq < 64; sq++) {
            if (!squaresProcessed[sq]) { processSquare(posn, sq, colour, otherMoves, checkInfo, kingsSquare, checkMask); }
         }

         if (!posn.isKingInCheck()) {
            if (canCastleKingsside(posn, colour)) { kingMoves.add(Move.KINGS_CASTLING_MOVE[colour.ordinal()]); }
            if (canCastleQueensside(posn, colour)) { kingMoves.add(Move.QUEENS_CASTLING_MOVE[colour.ordinal()]); }
         }
      }

      // process king moves to make sure the king hasn't moved into check
      Iterator<IMove> kingMoveIter = kingMoves.iterator();
      while (kingMoveIter.hasNext()) {
         IMove kingsMove = kingMoveIter.next();
         if (kingIsInCheckAfterKingsMove(posn, kingsMove, colour)) { kingMoveIter.remove(); }
      }

      // *** have now found valid moves
      // now process to see if our moves are checking the enemy king
      // ***

      // collect all the moves
      List<IMove> allMoves = new ArrayList<>(64);
      if (!kingInDoubleCheck) {
         for (Ray ray : Ray.values()) {
            allMoves.addAll(movesWithStartSqOnRay[ray.ordinal()]);
         }
         allMoves.addAll(otherMoves);
      }
      // could process king moves separately in the following code, but not really necessary,
      // since isKingInCheckAfterMove(...) copes with king moves as well
      allMoves.addAll(kingMoves);

      // ***
      // now process checks against _opposing_ king
      // ***
      RayCacheInfo[] squaresAttackingOpponentsKing = new RayCacheInfo[64]; // this stores the result of processed squares (for sliding pieces)
      int opponentsKingsSquare = posn.getKingsSquare(colour.opposite());
      ListIterator<IMove> iter = allMoves.listIterator();
      while (iter.hasNext()) {
         IMove m = iter.next();

         List<PieceSquareInfo> checkSquares = isOpponentsKingInCheckAfterMove(posn, m, opponentsKingsSquare, colour.opposite(), squaresAttackingOpponentsKing);
         if (!checkSquares.isEmpty()) {
            iter.remove();
            iter.add(new CheckMoveDecorator(m, checkSquares));
         }
      }

      return allMoves;
   }

   /**
    * If the king is already in check, this enum details the possibilities after a certain move.
    */
   enum BlockedCheckPossibility {
      NOT_BLOCKED, // move has not blocked the check
      CHECKING_PIECE_CAPTURED, // move has captured the checking piece
      RAY_BLOCKED; // move has blocked the ray along which the check was being made
   }

   /**
    * Would a move to 'targetSquareOfMove' block the given check, i.e. is it on the same ray and between the checker and the king?
    * <p>
    * Can be called when the king is not in check. In this case, checkInfo==null and true will be returned.
    * 
    * @param checkInfo          info about the checking piece. Can be null, in which case 'true' will always be returned.
    * @param targetSquareOfMove where our piece is moving to
    * @param kingsSquare
    * @param checkMask          if the square in the checkMask is set, then it blocks the check. Note: the check mask includes the square of
    *                           the checking piece, i.e. this will return true if we're dealing with a capture of that piece
    * @return true if blocks (or captures) the checker
    */
   private boolean moveToSquareBlocksPossibleCheck(PieceSquareInfo checkInfo, int targetSquareOfMove, int kingsSquare, BitSetUnifier checkMask) {
      // either not in check, in which case every move is allowed, or use the checkMask
      return (checkInfo == null) || checkMask.get(targetSquareOfMove);
   }

   /**
    * Processes king's moves. Returns true if the king is (still) in check after moving.
    * 
    * @param posn      current posn
    * @param kingsMove the king's move
    * @param colour    king's colour
    * @return true if in check after move
    */
   /* package protected */ boolean kingIsInCheckAfterKingsMove(Position posn, IMove kingsMove, Colour colour) {
      int captureSquare = kingsMove.isCapture() ? kingsMove.getTarget() : -1;
      Colour opponentsColour = colour.opposite();
      if (posn.isKingInCheck()) {
         // if already in check, cannot move along the same ray as the checker (unless it's a capture)
         List<PieceSquareInfo> checkSquares = posn.getCheckSquares();
         for (PieceSquareInfo checkInfo : checkSquares) {
            // ignore if a pawn (can move away from pawn on the same ray w/o any problem)
            if (Piece.PAWN == checkInfo.piece()) { continue; }
            Ray rayBeforeMove = checkInfo.rayToKing() != null ? checkInfo.rayToKing() : Ray.findRayBetween(kingsMove.getOrigin(), checkInfo.square());
            // ignore if not on ray (==> knight check)
            if (rayBeforeMove != null) {
               if (checkInfo.rayToKing() == null) { checkInfo.setRayToKing(rayBeforeMove); }
               Ray rayAfterMove = Ray.findRayBetween(kingsMove.getTarget(), checkInfo.square());
               if (rayBeforeMove == rayAfterMove && captureSquare != checkInfo.square()) { return true; }
               if (rayAfterMove != null && rayBeforeMove == rayAfterMove.getOpposite()) { return true; }
            }
         }
      }

      // having reached this point, the king has successfully moved out of an existing check
      // now check if it's moved into a check

      for (int sq : pawnCaptures[colour.ordinal()][kingsMove.getTarget()]) {
         if (sq != captureSquare && posn.matchesPieceTypeAndColour(sq, Pieces.generatePiece(Piece.PAWN, opponentsColour))) { return true; }
      }

      for (int sq : knightMoves[kingsMove.getTarget()]) {
         if (sq != captureSquare && posn.matchesPieceTypeAndColour(sq, Pieces.generatePiece(Piece.KNIGHT, opponentsColour))) { return true; }
      }

      // now need to process all rays from the new king's square.
      // TODO *** Careful, the position still stores the _old_ king's position.

      Ray ignoreRay1 = null, ignoreRay2 = null;
      // if the king was _not in check_ before, we don't need to check the direction
      // of travel (or the opposite direction)
      // e.g. if moving Kg1-f1 (west), then don't need to check west or east ray
      // For a capture: Kg1xf1 (west), we _must_ still check the west ray; but can
      // safely ignore the east ray
      if (!posn.isKingInCheck()) {
         Ray moveDirection = Ray.findRayBetween(kingsMove.getOrigin(), kingsMove.getTarget());
         if (!kingsMove.isCapture()) { ignoreRay1 = moveDirection; }
         ignoreRay2 = moveDirection.getOpposite();
      }

      for (Ray ray : Ray.values()) {
         if (ray == ignoreRay1 || ray == ignoreRay2) { continue; }
         PieceSquareInfo enemyPieceInfo = posn.opponentsPieceOnRay(colour, kingsMove.getTarget(), ray);
         if (enemyPieceInfo.piece() != null && enemyPieceInfo.square() != captureSquare && enemyPieceInfo.piece().canSlideAlongRay(ray)) { return true; }
      }
      return false;
   }

   /**
    * Is the opponent's king (colour 'colour') at 'kingsSquare' in check after my move 'm'? If yes, the checking square(s) will be returned.
    * 
    * @param posn                 current position
    * @param m                    the move
    * @param kingsSquare          opponent's king
    * @param colour               colour of king
    * @param squaresAttackingKing information about squares which attack the opponent's king
    * @return a list containing the squares which check the king (an empty list if not in check)
    */
   /* package protected */ List<PieceSquareInfo> isOpponentsKingInCheckAfterMove(Position posn, IMove m, int kingsSquare, Colour colour,
         RayCacheInfo[] squaresAttackingKing) {
      List<PieceSquareInfo> checkSquares = new ArrayList<>(2);
      boolean updateCache = squaresAttackingKing != null && !m.isCapture() && !m.isPromotion();

      // (1) does the moving piece check the king directly?
      addIfNotNull(checkSquares, moveAttacksSquare(posn, m, kingsSquare, squaresAttackingKing));

      // build cache of Rays from origin..king (and target..king) to save calling
      // findRayBetween

      // (2) is there a piece on the ray origin..kingssquare which _now_ attacks the
      // king (i.e. discovered check)?
      // TODO here check the 'squaresAttackingKing' cache!
      Ray rayFromOriginToKing = Ray.findRayBetween(m.getOrigin(), kingsSquare);
      if (rayFromOriginToKing != null) {
         Ray rayFromTargetToKing = Ray.findRayBetween(m.getTarget(), kingsSquare); // could be null
         // don't search if the move is to a square on the same king's ray (pawn move, or
         // r/b/q where no path to enemy king)
         if (rayFromOriginToKing != rayFromTargetToKing) {
            int interveningSquare = interveningSquaresAreEmpty(posn, m.getOrigin(), kingsSquare, -1, rayFromOriginToKing);
            if (interveningSquare == -1) {
               if (updateCache) { updateCacheClearPath(squaresAttackingKing, m.getOrigin(), kingsSquare, rayFromOriginToKing); }
               // squares from origin to king are empty, so see if there's an opponent's piece
               // on the opposite ray starting at origin
               PieceSquareInfo enemyPieceInfo = posn.opponentsPieceOnRay(colour, m.getOrigin(), rayFromOriginToKing.getOpposite());
               // enemy piece found, and capable of checking the king?
               if (enemyPieceInfo.piece() != null && enemyPieceInfo.piece().canSlideAlongRay(rayFromOriginToKing)) { checkSquares.add(enemyPieceInfo); }
            } else { // ray to king, but intervening squares not empty
               if (updateCache) { updateCacheNoClearPath(squaresAttackingKing, m.getOrigin(), interveningSquare, rayFromOriginToKing); }
            }
         }
      } else { // no ray to king
         if (updateCache) { squaresAttackingKing[m.getOrigin()] = RayCacheInfo.noRayToKing(); }
      }
      return checkSquares;
   }

   // update cache "clearPath" from origin to target. "origin" and "target"
   // themselves are not changed
   private void updateCacheClearPath(RayCacheInfo[] squaresAttackingKing, int origin, int target, Ray ray) {
      for (int sq : Ray.raysList[origin][ray.ordinal()]) {
         if (sq == target) { break; }
         squaresAttackingKing[sq] = RayCacheInfo.clearPath(ray);
         if (verbose) { System.out.println("cache: clear path, square " + Square.toSquare(sq)); }
      }
   }

   // update cache "noClearPath" from origin to target. "origin" and "target"
   // themselves are not changed
   private void updateCacheNoClearPath(RayCacheInfo[] squaresAttackingKing, int origin, int target, Ray ray) {
      for (int sq : Ray.raysList[origin][ray.ordinal()]) {
         if (sq == target) { break; }
         squaresAttackingKing[sq] = RayCacheInfo.noClearPath(ray);
         if (verbose) { System.out.println("cache: no clear path, square " + Square.toSquare(sq)); }
      }
   }

   /**
    * Returns true if the given move would attack the given square. Can be used e.g. to see if a move will check the opponent's king.
    * 
    * NB if a king move, always returns null.
    * 
    * @param posn                     current position
    * @param move                     the move to test
    * @param targetSq                 square which might be attacked by the move
    * @param squaresWhichAttackTarget stores the result of processed squares (for sliding pieces). Can be null.
    * @return either null or an object detailing the piece and checking square
    */
   private PieceSquareInfo moveAttacksSquare(Position posn, IMove move, int targetSq, RayCacheInfo[] squaresWhichAttackTarget) {
      byte movingPiece = posn.pieceAt(move.getOrigin());
      if (Pieces.isKing(movingPiece)) {
         int slot = move.isKingssideCastling() ? 0 : move.isQueenssideCastling() ? 1 : -1;
         if (slot != -1) {
            return pieceAttacksSquare(posn, Piece.ROOK, rooksSquareAfterCastling[Pieces.colourOf(movingPiece).ordinal()][slot], targetSq, -1, -1,
                  squaresWhichAttackTarget);
         } else {
            return null;
         }
      } else if (Pieces.isPawn(movingPiece)) {
         if (move.isPromotion()) {
            // TODO for now, don't use cache
            return pieceAttacksSquare(posn, Pieces.toPiece(move.getPromotedPiece()), move.getTarget(), targetSq, move.getOrigin(), -1, null);
         } else {
            for (int sq : pawnCaptures[Pieces.colourOf(movingPiece).ordinal()][move.getTarget()]) {
               if (sq == targetSq) { return new PieceSquareInfo(Piece.PAWN, move.getTarget()); }
            }
            return null;
         }
      } else {
         return pieceAttacksSquare(posn, Pieces.toPiece(movingPiece), move.getTarget(), targetSq, move.getOrigin(), move.isCapture() ? move.getTarget() : -1,
               squaresWhichAttackTarget);
      }
   }

   /**
    * Whether a (possibly hypothetical) piece 'piece' at 'origin' attacks 'target'. Should not be called for kings or pawns.
    * 
    * @param posn
    * @param piece
    * @param origin
    * @param target
    * @param squareToIgnore             if set,this square will be treated as 'empty'. Useful for pawn promotions.
    * @param cacheCaptureSquareToIgnore if set, the cached value for this square will not be used. (useful for captures). Also switches off
    *                                   updating the cache.
    * @param squaresWhichAttackTarget   stores the result of processed squares (for sliding pieces). <B>Will be ignored if cacheSquareToIgnore
    *                                   == -1</B>.
    * @return either null (does not attack square) or the piece / checking square info
    */
   private PieceSquareInfo pieceAttacksSquare(Position posn, Piece piece, int origin, int target, int squareToIgnore, int cacheCaptureSquareToIgnore,
         RayCacheInfo[] squaresWhichAttackTarget) {
      if (piece == Piece.KNIGHT) { return knightMoves[origin].contains(target) ? new PieceSquareInfo(Piece.KNIGHT, origin) : null; } // TODO
                                                                                                                                     // colour
      // only use cache if this isn't a capture
      var canUseCache = squaresWhichAttackTarget != null && cacheCaptureSquareToIgnore == -1;

      // see if result has already been calculated (ignore if cacheSquareToIgnore is set)
      if (canUseCache) {
         RayCacheInfo cacheInfo = squaresWhichAttackTarget[origin];
         if (cacheInfo != null) {
            if (cacheInfo.state != RayCacheState.CLEAR_PATH_TO_KING) { return null; }
            return piece.canSlideAlongRay(cacheInfo.rayBetween) ? new PieceSquareInfo(piece, origin) : null;
         }
      }

      Ray rayBetween = Ray.findRayBetween(origin, target);
      if (rayBetween == null) {
         if (squaresWhichAttackTarget != null) { squaresWhichAttackTarget[origin] = RayCacheInfo.noRayToKing(); }
         return null;
      }
      if (!piece.canSlideAlongRay(rayBetween)) { return null; }
      // now know that the piece at 'origin' could attack the targetSq, if there is a clear path
      int interveningSquare = interveningSquaresAreEmpty(posn, origin, target, squareToIgnore, rayBetween);
      if (interveningSquare == -1) {
         if (canUseCache) {
            if (verbose) { System.out.println("cache clear path, square " + Square.toSquare(origin)); }
            squaresWhichAttackTarget[origin] = RayCacheInfo.clearPath(rayBetween);
            updateCacheClearPath(squaresWhichAttackTarget, origin, target, rayBetween);
         }
         return new PieceSquareInfo(piece, origin);
      } else {
         // also store a negative result
         if (canUseCache) {
            // can only cache squares up to intervening piece
            if (verbose) { System.out.println("cache noClearPath, square " + Square.toSquare(origin)); }
            squaresWhichAttackTarget[origin] = RayCacheInfo.noClearPath(rayBetween);
            updateCacheNoClearPath(squaresWhichAttackTarget, origin, interveningSquare, rayBetween);
         }
         return null;
      }
   }

   /**
    * Finds all pseudo-legal moves (i.e. not checked for pins) for a KING at 'startSq'.
    *
    * Only for King moves.
    * 
    * @param posn
    * @param startSq                   square to process
    * @param colour
    * @param moves                     king moves will be added to this list
    * @param kingsForbiddenSquaresMask defines the squares which would still leave the king in check; therefore cannot move to any of these
    *                                  squares
    */
   /* package */ void generateKingMoves(Position posn, int startSq, Colour colour, List<IMove> moves, BitSetUnifier kingsForbiddenSquaresMask) {

      // more or less a duplicate of processSquare, using 'possibleMoves' to filter for in-check/adjacent to king moves.
      // next[0] or next[1] doesn't matter, both are set to be the same

      // TODO one thing this doesn't check for, is the king moving away on the same checking ray e.g. Ra3 checks Kc3 and king moves to Kd3.

      MoveNode targetNode = moveNodes[Piece.KING.ordinal()][startSq];
      while (targetNode != null) {
         IMove move = null;
         // a) generate a move if the target square is empty
         // b) move to 'next direction' next[1] if a piece is occupying the square
         byte targetSquareContents = posn.pieceAt(targetNode.to);
         if (targetSquareContents == 0) {
            move = targetNode.move;
            targetNode = targetNode.next[0];
         } else if (colour == Pieces.colourOf(targetSquareContents)) {
            // our own piece is blocking
            targetNode = targetNode.next[1];
         } else {
            // capture
            move = targetNode.captureMove;
            targetNode = targetNode.next[1];
         }
         if (move != null && !kingsForbiddenSquaresMask.get(move.getTarget())) { moves.add(move); }
      }
   }

   /**
    * Finds all pseudo-legal moves (i.e. not checked for pins) for a knight at 'startSq'.
    *
    * @param posn
    * @param startSq   square to process
    * @param colour
    * @param moves     moves will be added to this list
    * @param checkMask if the square in the checkMask is set, then it blocks the check.
    */
   /* package */ void generateKnightMoves(Position posn, int startSq, Colour colour, List<IMove> moves, BitSetUnifier checkMask) {
      MoveNode targetNode = moveNodes[Piece.KNIGHT.ordinal()][startSq];
      while (targetNode != null) {
         IMove move = null;
         // a) generate a move if the target square is empty
         // b) move to 'next direction' next[1] if a piece is occupying the square
         byte targetSquareContents = posn.pieceAt(targetNode.to);
         if (targetSquareContents == 0) {
            move = targetNode.move;
            targetNode = targetNode.next[0];
         } else if (colour == Pieces.colourOf(targetSquareContents)) {
            // our own piece is blocking
            targetNode = targetNode.next[1];
         } else {
            // capture
            move = targetNode.captureMove;
            targetNode = targetNode.next[1];
         }
         if (move != null && checkMask.get(move.getTarget())) { moves.add(move); }
      }
   }

   /**
    * Finds all pseudo-legal moves (i.e. not checked for pins) for a piece at 'startSq'.
    * 
    * If our king is currently in check, the move has to block the check or capture the checking piece.
    * 
    * King squares are not processed here, see processKingsSquare().
    * 
    * @param posn        position
    * @param startSq     square to process
    * @param colour      colour to move
    * @param moves       moves will be added to this list
    * @param checkInfo   non-null if our king is currently in check
    * @param kingsSquare position of our king; relevant if checkInfo!=null
    * @param checkMask   if the square in the checkMask is set, then it blocks the check.
    */
   private void processSquare(Position posn, int startSq, Colour colour, List<IMove> moves, PieceSquareInfo checkInfo, int kingsSquare,
         BitSetUnifier checkMask) {
      if (!posn.squareIsEmpty(startSq) && posn.colourOfPieceAt(startSq) == colour) {
         final byte pieceOnStartSq = posn.pieceAt(startSq);
         if (Pieces.isPawn(pieceOnStartSq)) {
            moves.addAll(generatePawnMoves(posn, startSq, colour, checkInfo, kingsSquare, checkMask));
         } else if (Pieces.isKnight(pieceOnStartSq)) {
            generateKnightMoves(posn, startSq, colour, moves, checkMask);
         } else if (Pieces.isKing(pieceOnStartSq)) {
            throw new IllegalStateException(String.format("called processSquare (sq=%s) with King:%n%s", Square.toSquare(startSq), posn));
         } else {
            MoveNode targetNode = moveNodes[Pieces.toPiece(pieceOnStartSq).ordinal()][startSq];
            while (targetNode != null) {
               IMove move = null;
               // a) generate a move if the target square is empty or is occupied by an enemy piece
               // b) move to 'next direction' next[1] if a friendly piece is occupying the square or a capture
               byte targetSquareContents = posn.pieceAt(targetNode.to);
               if (targetSquareContents == 0) {
                  move = targetNode.move;
                  targetNode = targetNode.next[0];
               } else if (colour == Pieces.colourOf(targetSquareContents)) {
                  // our own piece is blocking
                  targetNode = targetNode.next[1];
               } else {
                  // capture
                  move = targetNode.captureMove;
                  targetNode = targetNode.next[1];
               }
               if (move != null && checkMask.get(move.getTarget())) { moves.add(move); }
            }
         }
      }

   }

   private <T> void addIfNotNull(List<T> list, T object) {
      if (object != null) { list.add(object); }
   }

   /**
    * Information about the state of a particular square along a ray from our king.
    */
   record SquareInfo(State state, int square) {
      enum State {
         /** piece is pinned against our king */
         PINNED,
         /** path to our king is blocked further along the ray */
         PATH_TO_KING_BLOCKED,
         /**
          * an enemy piece was found along the ray to our king, but it cannot check along this ray
          */
         ENEMY_PIECE_FOUND_CANNOT_CHECK,
         /** an enemy piece was not found along the ray */
         ENEMY_PIECE_NOT_FOUND,
         /** not yet analysed */
         UNKNOWN;
      }
   }

   /**
    * Is our king in check after the given move? I.e. is there an enemy piece capable of giving check on the appropriate ray.
    * 
    * @param posn
    * @param m                      the move to check
    * @param ray                    the ray to check
    * @param kingsSquare
    * @param colour
    * @param squareInfo             stores the squares of pinned pieces; use new int[] { -1, -1, -1, -1, -1, -1, -1, -1 } if not applicable.
    * @param squaresWhichAttackKing a cache of previously processed squares
    * @return true if the given move leaves the king in check
    */
   private boolean moveLeavesOurKingInCheckAlongRay(Position posn, IMove m, Ray ray, int kingsSquare, Colour colour, SquareInfo[] squareInfo,
         RayCacheInfo[] squaresWhichAttackKing) {
      SquareInfo rayInfo;
      if (squareInfo[ray.ordinal()] == null) {
         rayInfo = new SquareInfo(SquareInfo.State.UNKNOWN, -1);
         squareInfo[ray.ordinal()] = rayInfo;
      } else {
         rayInfo = squareInfo[ray.ordinal()];
      }
      // return immediately if this move involves a pinned piece...
      if (rayInfo.state == SquareInfo.State.PINNED && m.getOrigin() == rayInfo.square) {
         // ... but a move along the ray is still ok (includes capture along same ray)
         if (moveOnSameRayOrOpposite(m, ray)) {
            if (verbose) { System.out.println(String.format("move %s with pinned piece is ok because along same ray as pin", m)); }
            return false;
         } else {
            if (verbose) { System.out.println(String.format("move %s illegal b/c piece at %s is pinned", m, Square.toSquare(rayInfo.square))); }
            return true;
         }
      }
      // no further processing if this move is 'further' along a ray where a pinned
      // piece has already been found
      // (nb this method is called with moves sorted with squares closest to king first)
      if (rayInfo.state == State.PINNED) {
         if (verbose) { System.out.println(String.format("not processing move %s b/c piece at %s is pinned", m, Square.toSquare(rayInfo.square))); }
         return false;
      }
      // same again: if a piece is blocking the path to the king, the currently moving
      // piece cannot be pinned
      if (rayInfo.state == State.PATH_TO_KING_BLOCKED) {
         // one exception: enpassant where the pawn that's being captured is on the ray
         // to the king
         // we just keep going here in that case (ignoring the cache)
         if (!m.isEnpassant()) {
            if (verbose) { System.out.println(String.format("not processing move %s b/c ray to king is blocked", m)); }
            return false;
         }
      }
      if (verbose) {
         System.out.println(String.format("processing move %s: sq %s is on ray %s from King (%s)", m, Square.toSquare(m.getOrigin()), ray.getAbbreviation(),
               Square.toSquare(kingsSquare)));
      }

      if (m.isEnpassant()) {
         if (interveningSquaresAreEmpty(posn, kingsSquare, m.getOrigin(), m.getSquareOfPawnCapturedEnpassant(), ray) != -1) { return false; }
      } else {
         // has the ray already been analysed? Again, the cache must be ignored for
         // enpassant
         if (rayInfo.state == State.ENEMY_PIECE_NOT_FOUND || rayInfo.state == State.ENEMY_PIECE_FOUND_CANNOT_CHECK) {
            if (verbose) { System.out.println(String.format("not further checking move %s for enemy pieces since state is %s", m, rayInfo.state)); }
            return false;
         }
         if (moveOnSameRayOrOpposite(m, ray)) {
            if (verbose) { System.out.println(String.format("move %s cannot leave king in check because moving along same ray", m)); }
            return false;
         }
         // If there's a piece between the moving piece and the king, then the king
         // cannot be
         // left in check, so don't process this move anymore
         if (interveningSquaresAreEmpty(posn, kingsSquare, m.getOrigin(), -1, ray) != -1) {
            squareInfo[ray.ordinal()] = new SquareInfo(State.PATH_TO_KING_BLOCKED, m.getOrigin());
            if (verbose) { System.out.println(String.format("stored PATH_TO_KING_BLOCKED for square %s", Square.toSquare(m.getOrigin()))); }
            return false;
         }
      }

      // .. now see if there's an enemy piece on this ray
      PieceSquareInfo enemyPieceInfo = posn.opponentsPieceOnRay(colour, m.getOrigin(), ray, m.isEnpassant() ? m.getSquareOfPawnCapturedEnpassant() : -1);
      if (enemyPieceInfo.piece() != null) {
         if (verbose) {
            System.out.println(String.format(".. found enemy piece %s at %s on same ray whilst processing move %s", enemyPieceInfo.piece(),
                  Square.toSquare(enemyPieceInfo.square()), m));
         }
         // ... which is capable of checking the king
         if (enemyPieceInfo.piece().canSlideAlongRay(ray)) {
            // be on the safe side, in case the pawn removed by enpassant is on the ray in question
            if (!m.isEnpassant()) { squareInfo[ray.ordinal()] = new SquareInfo(State.PINNED, m.getOrigin()); }
            if (!moveOnSameRayOrOpposite(m, ray)) {
               if (verbose) {
                  System.out.println(String.format("piece at %s is pinned by %s at %s", Square.toSquare(m.getOrigin()), enemyPieceInfo.piece(),
                        Square.toSquare(enemyPieceInfo.square())));
               }
               return true;
            } else {
               if (verbose) { System.out.println(String.format("move %s with pinned piece is ok because along same ray as pin", m)); }
               return false;
            }
         } else {
            if (verbose) { System.out.println(String.format("enemy piece found on ray %s but not capable of checking king", ray)); }
            squareInfo[ray.ordinal()] = new SquareInfo(State.ENEMY_PIECE_FOUND_CANNOT_CHECK, m.getOrigin());
         }
      } else {
         if (verbose) { System.out.println(String.format("no enemy piece found on ray %s", ray)); }
         squareInfo[ray.ordinal()] = new SquareInfo(State.ENEMY_PIECE_NOT_FOUND, m.getOrigin());
      }
      return false;
   }

   /**
    * Are all squares between origin and target empty?
    * 
    * @param origin         start square (e.g. kings square)
    * @param target         target square (e.g. move's origin square)
    * @param squareToIgnore optional square to ignore, i.e. this square will be treated as being empty. E.g. contains a pawn, which could be
    *                       taken enpassant
    * @param ray            the required ray to check
    * @return -1 if all squares between origin and target along the given ray are empty, otherwise the square which is not empty
    */
   private int interveningSquaresAreEmpty(Position posn, int origin, int target, int squareToIgnore, Ray ray) {
      IntFunction<Boolean> isEmptySquare = sq -> { return sq == squareToIgnore || posn.squareIsEmpty(sq); };
      return interveningSquaresAreEmpty(isEmptySquare, origin, target, ray);
   }

   /**
    * Are all squares between origin and target empty? 'empty' is defined by the isSquareEmptyPredicate (can be different for enpassant).
    * 
    * @param isSquareEmptyPredicate predicate to use to check if a square is empty
    * @param origin                 start square (e.g. kings square)
    * @param target                 target square (e.g. move's origin square)
    * @param ray                    the required ray to check
    * @return -1 if all squares between origin and target along the given ray are empty, otherwise the square which is not empty
    */
   private int interveningSquaresAreEmpty(IntFunction<Boolean> isSquareEmptyPredicate, int origin, int target, Ray ray) {
      for (int interveningSq : Ray.raysList[origin][ray.ordinal()]) {
         if (interveningSq == target) { return -1; }
         if (!isSquareEmptyPredicate.apply(interveningSq)) { return interveningSq; }
      }
      // have either hit the target square, or the ray is finished (==> origin and
      // target are not on the same ray)
      return -1;
   }

   /**
    * Inspects all moves <B>on the given rays</B> (must be sorted with origin squares closest to kingsSquare occurring first). If after the
    * move the king is in check, then the piece was pinned and the move will be removed from the list.
    * 
    * @param posn
    * @param kingsSquare
    * @param colour
    * @param movesOnRay  the moves to inspect, stored for each ray as seen from the king's square, sorted with origin squares closest to king
    *                    first
    */
   private void removeMovesLeavingKingInCheckAlongRay(Position posn, int kingsSquare, Colour colour, List<IMove>[] movesOnRay) {
      SquareInfo[] pinnedPieces = new SquareInfo[8]; // stores squares of pinned pieces for each ray
      RayCacheInfo[] squaresWhichAttackKing = new RayCacheInfo[64]; // this stores the result of processed squares
      // (for sliding pieces)
      for (Ray ray : Ray.values()) {
         Iterator<IMove> moveIter = movesOnRay[ray.ordinal()].iterator();
         while (moveIter.hasNext()) {
            IMove m = moveIter.next();
            if (moveLeavesOurKingInCheckAlongRay(posn, m, ray, kingsSquare, colour, pinnedPieces, squaresWhichAttackKing)) { moveIter.remove(); }
         }
      }
   }

   /**
    * @param m   move
    * @param ray ray
    * @return true if the given move is along the given ray (or its opposite) (i.e. the piece is not pinned)
    */
   private boolean moveOnSameRayOrOpposite(IMove m, Ray ray) {
      return ray.onSameRay(m.getOrigin(), m.getTarget()) || ray.getOpposite().onSameRay(m.getOrigin(), m.getTarget());
   }

   // if the king is in check is not verified by this method
   /* package */ boolean canCastleKingsside(Position posn, Colour colour) {
      if (!posn.canCastleKingsside(colour)) { return false; }
      int colourOrd = colour.ordinal();
      final Colour oppositeColour = colour.opposite();
      if (!Pieces.isRook(posn.pieceAt(rooksCastlingSquareIndex[colourOrd][0]))) {
         throw new IllegalStateException("rook not present for " + colour + " castling king's side? posn:\n" + posn);
      }

      for (int sq : unoccupiedSquaresKingssideCastling[colourOrd]) {
         if (!posn.squareIsEmpty(sq)) { return false; }
      }

      /*
       * @formatter:off
       *  
       * Cannot castle over a square in check.
       *
       * From White's POV:
       *   Previous algorithm looked for opponent's pawns on the 2nd rank, then knights, and then inspected the rays NW, N, NE from the two intervening squares f1, g1 for 'sliding pieces'.
       *   Now checks the knights and then just checks the rays, and if a pawn is found make sure it's on the 2nd rank.
       *   Still checks the knights first, since this is probably cheaper then checking the rays.
       *
       * @formatter:on
       */

      final byte oppositeColourKnight = Pieces.generateKnight(oppositeColour);
      for (int sq : knightSquaresKingssideCastling[colourOrd]) {
         if (posn.matchesPieceTypeAndColour(sq, oppositeColourKnight)) { return false; }
      }
      final int requiredPawnRank = colour == Colour.WHITE ? 1 : 6; // pawn must be on this rank to prevent castling
      for (int sq : unoccupiedSquaresKingssideCastling[colourOrd]) {
         for (Ray ray : Ray.RAYS_TO_CHECK_KINGSSIDE_CASTLING[colourOrd]) {
            PieceSquareInfo enemyPieceInfo = posn.opponentsPieceOnRay(colour, sq, ray);
            // found piece capable of checking the king? (sliding piece on the ray, or a pawn one rank away)
            if (enemyPieceInfo.piece() != null && //
                  ((enemyPieceInfo.piece() == Piece.PAWN && Square.toSquare(enemyPieceInfo.square()).rank() == requiredPawnRank)
                        || enemyPieceInfo.piece().canSlideAlongRay(ray))) {
               return false;
            }
         }
      }
      return true;
   }

   // if the king is in check is not verified by this method
   /* package */ boolean canCastleQueensside(Position posn, Colour colour) {
      if (!posn.canCastleQueensside(colour)) { return false; }
      int colourOrd = colour.ordinal();
      if (!Pieces.isRook(posn.pieceAt(rooksCastlingSquareIndex[colourOrd][1]))) {
         throw new IllegalStateException("rook not present for " + colour + " castling queen's side? posn:\n" + posn);
      }
      for (int sq : unoccupiedSquaresQueenssideCastling[colourOrd]) {
         if (!posn.squareIsEmpty(sq)) { return false; }
      }

      // cannot castle over a square in check
      // (see description in canCastleKingsside)
      final byte oppositeColourKnight = Pieces.generateKnight(colour.opposite());
      for (int sq : knightSquaresQueenssideCastling[colourOrd]) {
         if (posn.matchesPieceTypeAndColour(sq, oppositeColourKnight)) { return false; }
      }
      final int requiredPawnRank = colour == Colour.WHITE ? 1 : 6; // pawn must be on this rank to prevent castling
      for (int sq : unoccupiedSquaresQueenssideCastling[colourOrd]) {
         // the b1/b8 squares don't need to be inspected for the 'square in check' calculation:
         if (sq == Square.b1.index() || sq == Square.b8.index()) { continue; }
         for (Ray ray : Ray.RAYS_TO_CHECK_QUEENSSIDE_CASTLING[colourOrd]) {
            PieceSquareInfo enemyPieceInfo = posn.opponentsPieceOnRay(colour, sq, ray);
            // found piece capable of checking the king? (sliding piece on the ray, or a pawn one rank away)
            if (enemyPieceInfo.piece() != null && //
                  ((enemyPieceInfo.piece() == Piece.PAWN && Square.toSquare(enemyPieceInfo.square()).rank() == requiredPawnRank)
                        || enemyPieceInfo.piece().canSlideAlongRay(ray))) {
               return false;
            }
         }
      }
      return true;
   }

   /**
    * Generates moves for pawn at 'startSq'. If our king is already in check, then the move must capture at the checking square or block the
    * ray. This information is stored in the 'checkMask'.
    * 
    * @param posn
    * @param startSq
    * @param colour    colour of moving side
    * @param checkInfo non-null if our king is in check
    * @param checkMask if the square in the checkMask is set, then it blocks the check.
    * @return all pawn moves
    */
   private List<IMove> generatePawnMoves(Position posn, int startSq, Colour colour, PieceSquareInfo checkInfo, int kingsSquare, BitSetUnifier checkMask) {
      List<IMove> moves = new ArrayList<>();
      // following cases ('reverse' for black pawns):
      // - pawn on 2nd rank can move 1 or two squares forward
      // - capturing diagonally
      // - pawn on 7th rank can promote
      // - enpassant

      // if king already in check, then a move has to block or capture:
      // - normal pawn move: must block ray
      // - pawn capture (including promotion capture or enpassant): captures checking piece or blocks ray

      // this cannot be null unless we're calling it for a pawn on the 1st or 8th rank -- which should be impossible
      MoveNode currentNode = pawnMoves[colour.ordinal()][startSq];
      while (currentNode != null) {
         byte targetSquareContents = posn.pieceAt(currentNode.to);
         // generate a move if the target square is empty
         if (targetSquareContents == 0) {
            if (moveToSquareBlocksPossibleCheck(checkInfo, currentNode.to, kingsSquare, checkMask)) { moves.add(currentNode.move); }
            currentNode = currentNode.next[0]; // process any further moves (2 squares forward, or promotion)
         } else {
            break; // blocked by a piece
         }
      }

      // captures.
      // NB a possible enpassant move will be rejected here (since target square is empty) but will be processed in the following block
      currentNode = pawnCaptureNodes[colour.ordinal()][startSq];
      while (currentNode != null) {
         byte targetSquareContents = posn.pieceAt(currentNode.to);
         if (targetSquareContents == 0 || colour == Pieces.colourOf(targetSquareContents)) {
            // our own piece is blocking
         } else {
            // If the check still exists after this first promotion move, then it will for
            // the other moves too and therefore we don't need to evaluate them
            if (!moveToSquareBlocksPossibleCheck(checkInfo, currentNode.captureMove.getTarget(), kingsSquare, checkMask)) {
               // ignore move (and all further promotion moves if present)
            } else {
               moves.add(currentNode.captureMove);
               // add all further promotion moves if present -- leave 'currentNode' alone so as not to disturb the outer loop
               var node = currentNode;
               while ((node = node.next[0]) != null) {
                  moves.add(node.captureMove);
               }
            }
         }
         currentNode = currentNode.next[1];
      }

      if (posn.getEnpassantSquare() != null) {
         final int epSquare = posn.getEnpassantSquare().index();
         for (EnpassantInfo info : enpassantSquares[epSquare]) {
            if (startSq == info.squareOfPawnToBeTakenEnpassant) {
               // if in check, need to capture the checking piece
               if (checkInfo != null) {
                  if (info.enpassantMove.moveCapturesPiece(checkInfo.square()))
                     moves.add(info.enpassantMove);
               } else {
                  moves.add(info.enpassantMove);
               }
            }
         }
      }
      return moves;
   }

   /**
    * Stores info about a square in relation to a particular target square (often, the opposing king's square).
    */
   public static class RayCacheInfo {
      public enum RayCacheState {
         NO_RAY_TO_KING, CLEAR_PATH_TO_KING, NO_CLEAR_PATH_TO_KING;
      }

      /**
       * stores the ray to the target square or null. Only set if 'state' != NO_RAY_TO_KING
       */
      Ray rayBetween;
      /** whether there's a clear path to the target */
      RayCacheState state;

      private RayCacheInfo(Ray rayBetween, RayCacheState state) {
         this.rayBetween = rayBetween;
         this.state = state;
      }

      public static RayCacheInfo clearPath(Ray rayBetween) {
         return new RayCacheInfo(rayBetween, RayCacheState.CLEAR_PATH_TO_KING);
      }

      public static RayCacheInfo noClearPath(Ray rayBetween) {
         return new RayCacheInfo(rayBetween, RayCacheState.NO_CLEAR_PATH_TO_KING);
      }

      public static RayCacheInfo noRayToKing() {
         return new RayCacheInfo(null, RayCacheState.NO_RAY_TO_KING);
      }

      @Override
      public String toString() {
         return state + (rayBetween == null ? "" : "-" + rayBetween);
      }
   }
}
