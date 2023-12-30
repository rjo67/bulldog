package org.rjo.chess.bulldog.piece;

public enum Colour {
   WHITE, BLACK;

   static {
      WHITE.opposite = BLACK;
      BLACK.opposite = WHITE;
   }

   /**
    * using Colour.values creates a new array each time. Try to get around that with this static.
    */
   public static final Colour[] ALL_COLOURS = new Colour[] { WHITE, BLACK };

   private Colour opposite;

   public boolean opposes(Colour other) {
      return this.opposite == other;
   }

   public Colour opposite() {
      return opposite;
   }

}
