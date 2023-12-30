package org.rjo.chess.bulldog.eval;

import org.rjo.chess.bulldog.game.Position;

public interface SearchStrategy {

	MoveInfo findMove(Position posn);

	int getCurrentDepth();

	void incrementDepth(int increment);

	/**
	 * @return current number of nodes that have been searched
	 */
	default int getCurrentNbrNodesSearched() {
		return 0;
	}

}