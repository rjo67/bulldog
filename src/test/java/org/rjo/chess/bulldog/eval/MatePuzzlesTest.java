package org.rjo.chess.bulldog.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.rjo.chess.bulldog.game.Fen;
import org.rjo.chess.bulldog.game.Game;

public class MatePuzzlesTest {

	@Test
	public void test() {
		Game game = Fen.decode("5k1r/1R2R3/p3p1pp/4b3/1BnNr3/8/P1P5/5K2 w - - 1 0");
		SearchStrategy strat = new AlphaBeta3(System.out);
		strat.incrementDepth(2);
		System.out.println(strat.getCurrentDepth());
		MoveInfo mi = strat.findMove(game.getPosition());
		System.out.println(mi);
	}

	@Test
	public void mateInFour() {
		Game game = Fen.decode("4k2r/1R3R2/p3p1pp/4b3/1BnNr3/8/P1P5/5K2 w - - 1 0");
		// f7e7+, e8-d8 (if e8-f8, e7-c7+, any move, b7-b8#)
		// d6-c6+, d8-c8
		// c6-a7+, c8-d8
		// b7-d7+#
		SearchStrategy strat = new AlphaBeta3(System.out);
		strat.incrementDepth(3);
		System.out.println(strat.getCurrentDepth());
		MoveInfo mi = strat.findMove(game.getPosition());
		System.out.println(mi);
	}

	@Test
	public void mateInThree() {
		// Re6-g6+, Bg5
		// Qxg5+, Kh1
		// Qg2#
		// or
		// Re6-g6+, Kh1
		// Qf3+, Bxf3
		// Rxe1#

		Game game = Fen.decode("4r1k1/3n1ppp/4r3/3n3q/Q2P4/5P2/PP2BP1P/R1B1R1K1 b - - 0 1");
		SearchStrategy strat = new AlphaBeta3(System.out);
		strat.incrementDepth(3);
		System.out.println(strat.getCurrentDepth());
		MoveInfo mi = strat.findMove(game.getPosition());
		System.out.println(mi);
	}

	@Test
	public void mateInTwoA() {
		Game game = Fen.decode("4r1k1/pQ3pp1/7p/4q3/4r3/P7/1P2nPPP/2BR1R1K b - - 0 1");
		SearchStrategy strat = new AlphaBeta3(System.out);
		System.out.println(strat.getCurrentDepth());
		MoveInfo mi = strat.findMove(game.getPosition());
		System.out.println(mi);
		assertEquals("e5xh2+([h1xh2, e4-h4+])", mi.toString());
	}

	@Test
	public void mateInTwoB() {
		Game game = Fen.decode("r4R2/1b2n1pp/p2Np1k1/1pn5/4pP1P/8/PPP1B1P1/2K4R w - - 1 0");
		SearchStrategy strat = new AlphaBeta3(System.out);
		System.out.println(strat.getCurrentDepth());
		MoveInfo mi = strat.findMove(game.getPosition());
		assertEquals("h4-h5+([g6-h6, d6-f7+])", mi.toString());
		System.out.println(mi);
	}

}
