package org.rjo.chess.bulldog.piece;

import org.rjo.chess.bulldog.board.Ray;

/**
 * 
 * Comments taken from https://www.chessprogramming.org/Simplified_Evaluation_Function.
 * 
 * @author rich
 */
public enum Piece {

   PAWN(100, false, "", false, false, new int[] {}, // offsets not used for pawns
         /*
          * Important: array value [0] corresponds to square a1; [63] == h8. For black these values must be mirrored
          * 
          * Firstly the shelter in front of white short castle (long castle - it's symmetrical) - pawns at f2, g2 and h2 get bonuses. Additionally we
          * set negative values for f3 and smaller for g3 which both create holes around king. Pawn h2 have the same value on h2 and h3, so the
          * engine may create the hole if the situation calls for it. Moreover - if it gets position with a pawn at g3 and a bishop at g2, then it
          * still may play h3 or not. Therefore h3 has the same value as h2.
          * 
          * Zero value on f4, g4, h4 prevents playing with pawns in front of the king. Moving these pawns to f5, g5, h5 still brings nothing, but at
          * this moment we have the same values as on rank 2.
          * 
          * In the centre we have the most negative values on d2 and e2. We don't like these pawns. d3 and e3 aren't good either. Only d4 and e4 in
          * the centre. Even better on d5 and e5.
          * 
          * Beginning with rank 6th we give bonus for advanced pawns. On rank 7th even bigger.
          */
         // @formatter:off
         new int[]{
                  0,  0,   0,   0,   0,   0,  0,  0,
                  5, 10,  10, -20, -20,  10, 10,  5,
                  5, -5, -10,   0,   0, -10, -5,  5,
                  0,  0,   0,  20,  20,   0,  0,  0,
                  5,  5,  10,  25,  25,  10,  5,  5,
                 10, 10,  20,  30,  30,  20, 10, 10,
                 50, 50,  50,  50,  50,  50, 50, 50,
                  0,  0,   0,   0,   0,   0,  0,  0 }
         // @formatter:on
   ) {
      @Override
      public String fenSymbol(Colour colour) {
         return colour == Colour.WHITE ? "P" : "p";
      }
   },
   ROOK(500, true, "R", true, false, new int[] { -10, -1, 1, 10 },
         /*
          * The only ideas which came to my mind was to centralize, occupy the 7th rank and avoid a, h columns (in order not to defend pawn b3 from
          * a3).
          */
         // @formatter:off
         new int[] {
            0,  0,  0,  5,  5,  0,  0,  0,
           -5,  0,  0,  0,  0,  0,  0, -5,
           -5,  0,  0,  0,  0,  0,  0, -5,
           -5,  0,  0,  0,  0,  0,  0, -5,
           -5,  0,  0,  0,  0,  0,  0, -5,
           -5,  0,  0,  0,  0,  0,  0, -5,
            5, 10, 10, 10, 10, 10, 10,  5,
            0,  0,  0,  0,  0,  0,  0,  0 }
         // @formatter:on
   ), //
   KNIGHT(320, false, "N", false, false, new int[] { -21, -19, -12, -8, 8, 12, 19, 21 },
         /*
          * With knights we simply encourage them to go to the center. Standing on the edge is a bad idea. Standing in the corner is a terrible idea.
          */
         // @formatter:off
         new int[] {
                 -50, -40, -30, -30, -30, -30, -40, -50,
                 -40, -20,   0,   5,   5,   0, -20, -40,
                 -30,   5,  10,  15,  15,  10,   5, -30,
                 -30,   0,  15,  20,  20,  15,   0, -30,
                 -30,   5,  15,  20,  20,  15,   5, -30,
                 -30,   0,  10,  15,  15,  10,   0, -30,
                 -40, -20,   0,   0,   0,   0, -20, -40,
                 -50, -40, -30, -30, -30, -30, -40, -50, }
         // @formatter:on
   ), //
   BISHOP(330, true, "B", false, true, new int[] { -11, -9, 9, 11 },
         /* We avoid corners and borders. Additionally we prefer squares like b3, c4, b5, d3 and the central ones. */
         // @formatter:off
         new int[] {
                 -20, -10, -10, -10, -10, -10, -10, -20,
                 -10,   5,   0,   0,   0,   0,   5, -10,
                 -10,  10,  10,  10,  10,  10,  10, -10,
                 -10,   0,  10,  10,  10,  10,   0, -10,
                 -10,   5,   5,  10,  10,   5,   5, -10,
                 -10,   0,   5,  10,  10,   5,   0, -10,
                 -10,   0,   0,   0,   0,   0,   0, -10,
                 -20, -10, -10, -10, -10, -10, -10, -20, }
         // @formatter:on
   ), //
   QUEEN(900, true, "Q", true, true, new int[] { -11, -10, -9, -1, 1, 9, 10, 11 },
         /*
          * Generally with queen I marked places where I wouldn't like to have a queen. Additionally I slightly marked central squares to keep the
          * queen in the centre and b3, c2 squares
          */
         // @formatter:off
         new int[] {
               -20, -10, -10, -5, -5, -10, -10, -20,
               -10,   0,   5,  0,  0,   0,   0, -10,
               -10,   0,   5,  5,  5,   5,   0, -10,
                 0,   0,   5,  5,  5,   5,   0,  -5,
                -5,   0,   5,  5,  5,   5,   0,  -5,
               -10,   5,   5,  5,  5,   5,   0, -10,
               -10,   0,   0,  0,  0,   0,   0, -10,
               -20, -10, -10, -5, -5, -10, -10, -20 }
         // @formatter:on
   ), //
   KING(20000, false, "K", false, false, new int[] { -11, -10, -9, -1, 1, 9, 10, 11 },
         /* This is to make the king stand behind the pawn shelter. TODO different values for endgame */
         // @formatter:off
         new int[]{
               20, 30, 10, 0, 0, 10, 30, 20,
               20, 20, 0, 0, 0, 0, 20, 20,
               -10, -20, -20, -20, -20, -20, -20, -10,
               -20, -30, -30, -40, -40, -30, -30, -20,
               -30, -40, -40, -50, -50, -40, -40, -30,
               -30, -40, -40, -50, -50, -40, -40, -30,
               -30, -40, -40, -50, -50, -40, -40, -30,
               -30, -40, -40, -50, -50, -40, -40, -30 }
         // @formatter:on
   );

   /**
    * number of different piece types in the game.
    */
   public final static int DIFFERENT_PIECE_TYPES = Piece.values().length;

   /**
    * piece value in centipawns
    */
   private final int pieceValue;
   private final boolean slidingPiece;
   private final String symbol;
   // offsets refer to the Board.mailbox structure
   private final int[] moveOffsets;
   /*
    * Stores the piece-square values. https://www.chessprogramming.org/Simplified_Evaluation_Function
    */
   private final int[] squareValues;
   // following not relevant (i.e. false) for pawns / knights / kings
   private final boolean slidesHorizontallyOrVertically;
   private final boolean slidesDiagonally;

   private Piece(int pieceValue, boolean slidingPiece, String symbol, boolean movesHorizontallyOrVertically, boolean movesDiagonally, int[] moveOffsets,
         int[] squareValues) {
      this.pieceValue = pieceValue;
      this.slidingPiece = slidingPiece;
      this.symbol = symbol;
      this.slidesHorizontallyOrVertically = movesHorizontallyOrVertically;
      this.slidesDiagonally = movesDiagonally;
      this.moveOffsets = moveOffsets;
      this.squareValues = squareValues;
   }

   public boolean isSlidingPiece() { return slidingPiece; }

   public int[] getMoveOffsets() { return moveOffsets; }

   public String symbol(Colour colour) {
      return symbol; // == Colour.WHITE ? symbol : symbol.toLowerCase();
   }

   /**
    * Returns the FEN symbol for this piece. This is usually the 'symbol' in upper or lower case. Exception is the pawn.
    */
   public String fenSymbol(Colour colour) {
      return colour == Colour.WHITE ? symbol : symbol.toLowerCase();
   }

   public boolean slidesHorizontallyOrVertically() {
      return slidesHorizontallyOrVertically;
   }

   public boolean slidesDiagonally() {
      return slidesDiagonally;
   }

   /**
    * @return true if this piece can move along the given ray.
    */
   public boolean canSlideAlongRay(Ray ray) {
      return ((ray.isHorizontal() || ray.isVertical()) && slidesHorizontallyOrVertically) || (ray.isDiagonal() && slidesDiagonally());
   }

   public int calculatePieceSquareValue(int square, Colour col) {
      int sqValue = col == Colour.WHITE ? squareValues[square] : squareValues[63 - square];
      return pieceValue + sqValue;
   }

   public static Piece convertStringToPieceType(char ch) {
      if (ch == 'P' || ch == 'p') { return Piece.PAWN; }
      for (Piece p : Piece.values()) {
         if (p == Piece.PAWN) { continue; }
         if (ch == p.symbol.charAt(0) || ch == p.symbol.toLowerCase().charAt(0)) { return p; }
      }
      throw new IllegalStateException("unrecognised symbol: '" + ch + "'");
   }

}
