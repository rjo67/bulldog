package org.rjo.chess.bulldog.move;

import java.util.List;

import org.rjo.chess.bulldog.game.Position;
import org.rjo.chess.bulldog.piece.Colour;

/**
 * Simple interface for MoveGenerator, to enable other implementations to be plugged in.
 * 
 * @author rich
 */
public interface MoveGeneratorI {
   List<IMove> findMoves(Position posn, Colour colour);

}
