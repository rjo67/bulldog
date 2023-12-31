package org.rjo.chess.bulldog.eval;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rjo.chess.bulldog.game.Position;
import org.rjo.chess.bulldog.move.IMove;
import org.rjo.chess.bulldog.piece.Colour;

public class AlphaBeta3 implements SearchStrategy {
	private static final Logger LOG = LogManager.getLogger(AlphaBeta3.class);

	private static final int MIN_VAL = -99999;
	private static final int MAX_VAL = -MIN_VAL;

	private static boolean USE_ZOBRIST = true;

	private static int count = 0;

	private int startDepth = 4;

	private PrintStream outputStream;

	// private ZobristMap zobristMap;

	// how many times moves were made, i.e. new positions created
	private static int NBR_NODES_SEARCHED;
	// how many times 'evaluate' was called
	private static int NBR_POSNS_EVALUATED;

	public AlphaBeta3(PrintStream out) {
		this.outputStream = out;
	}

	@Override
	public MoveInfo findMove(Position posn) {
		NBR_NODES_SEARCHED = 0;
		NBR_POSNS_EVALUATED = 0;
		MoveTree moveTree = new MoveTree(null, null, startDepth, 0, 0);
		// if white currently to move, want to maximize. Otherwise minimize.
		MiniMax type = posn.getSideToMove() == Colour.WHITE ? MiniMax.MAX : MiniMax.MIN;

		long start = System.currentTimeMillis();
		SearchResult result = alphabeta(posn, startDepth, MIN_VAL, MAX_VAL, type, new Line(), moveTree);
		long duration = System.currentTimeMillis() - start;
		LOG.debug(moveTree.toString());
		LOG.info("evaluated {} nodes, {} posns, time: {}, result: {}", NBR_NODES_SEARCHED, NBR_POSNS_EVALUATED,
				timeTaken(duration), result);
		MoveInfo moveInfo = new MoveInfo();
		moveInfo.setMove(result.getLine().get().getMoves().pop());
		moveInfo.setLine(result.getLine().get());
		return moveInfo;
	}

	private void logDebug(String logLine, MiniMax evaluationType, int depth, Object... args) {
		if (LOG.isDebugEnabled()) {
			var indent = "                               ".substring(0, 9 - depth);
			List<Object> paramList = new ArrayList<>();
			paramList.add(indent);
			paramList.add(evaluationType);
			paramList.add(depth);
			paramList.addAll(Arrays.asList(args));
			LOG.debug("{}{} depth {}: " + logLine, paramList.toArray());
		}
	}

	/**
	 * the minimax value of n, searched to depth d. If the value is less than alpha,
	 * returns alpha. If greater than beta, returns beta.
	 *
	 * @param posn           current game position
	 * @param depth          current depth
	 * @param alpha          current min ("alpha")
	 * @param beta           current max ("beta")
	 * @param evaluationType whether max or min
	 * @param line           current line
	 * @param moveTree
	 * @return best result
	 */
	private SearchResult alphabeta(Position posn, int depth, int alpha, int beta, MiniMax evaluationType, Line line,
			MoveTree moveTree) {
		if (depth == 0) {
			NBR_POSNS_EVALUATED++;
			int score = posn.evaluate();
			logDebug("evaluating posn currentLine: {}, score {}", evaluationType, depth, line, score);
			return new SearchResult(score, startDepth, line);
		}

		Line currentBestLine = null;
		long startTime = System.currentTimeMillis();
		List<IMove> moves = orderMoves(posn, posn.findMoves(posn.getSideToMove()));
		int nbrMoves = moves.size();
		logDebug("currentLine: {}, alpha {}, beta {}, found {} moves in {}, moves: {}", evaluationType, depth, line,
				alpha, beta, nbrMoves, timeTaken(System.currentTimeMillis() - startTime), moves);
		int moveNbr = 0;
		int value;
		switch (evaluationType) {
		case MAX: // maximising player
			value = MIN_VAL;
			for (IMove move : moves) {
				moveNbr++;
				MoveTree moveEntry = new MoveTree(MiniMax.MAX, move, depth, alpha, beta);
				moveTree.addEntry(moveEntry);
				Position newPosn = posn.move(move);
				line.addMove(move, startDepth);
				NBR_NODES_SEARCHED++;
				logDebug("move {}/{}: {}, currentLine: {}, alpha {}, beta {}", evaluationType, depth, moveNbr, nbrMoves,
						move, line, alpha, beta);
				SearchResult result = alphabeta(newPosn, depth - 1, alpha, beta, MiniMax.MIN, line, moveEntry);
				moveEntry.setScore(result.getScore());
				value = Math.max(value, result.getScore());
				if (value > beta) {
					logDebug("beta cut-off, value {}, beta {}", evaluationType, depth, value, beta);
					moveEntry.addEvaluation(EvalType.BETA_CUTOFF);
					line.removeLastMove();
					break; /* beta cut-off */
				}
				if (result.getScore() > alpha) { // alpha = max (alpha, value)
					alpha = result.getScore();
					moveEntry.addEvaluation(EvalType.BESTSOFAR);
					if (result.getLine().isPresent()) {
						currentBestLine = new Line(result.getLine().get());
						if (depth == startDepth) {
							result.printUCI(outputStream);
						}
					}
					logDebug("saved new best line: {}, alpha {}, beta {}", evaluationType, depth, currentBestLine,
							alpha, beta);
				}
				line.removeLastMove();
			}
			// TODO replace this bit with posn.evaluate?
			if (moves.isEmpty()) {

				if (posn.isKingInCheck()) {
					logDebug("mate found, currentLine: {}", evaluationType, depth, line);
					// favour a mate in 5 rather than mate in 3
					return new SearchResult(MIN_VAL + (10 - depth), line, line.getMoves().size(), startDepth);// need to
																												// remain
																												// above
																												// MIN_VAL
																												// (??)
				} else {
					// statemate: evaluate as 0
					return new SearchResult(0, startDepth, line);
				}
			}
			// is possible to get here without having set 'currentBestLine'
			// e.g. have tried all possibilities but they were all outside of the
			// [alpha,beta] range
			return new SearchResult(value, startDepth, currentBestLine);

		case MIN: // minimising player
			value = MAX_VAL;
			for (IMove move : moves) {
				moveNbr++;
				MoveTree moveEntry = new MoveTree(MiniMax.MIN, move, depth, alpha, beta);
				moveTree.addEntry(moveEntry);
				Position newPosn = posn.move(move);
				line.addMove(move, startDepth);
				NBR_NODES_SEARCHED++;
				logDebug("move {}/{}: {}, currentLine: {}, min {}, max {}", evaluationType, depth, moveNbr, nbrMoves,
						move, line, alpha, beta);
				SearchResult result = alphabeta(newPosn, depth - 1, alpha, beta, MiniMax.MAX, line, moveEntry);
				moveEntry.setScore(result.getScore());
				value = Math.min(value, result.getScore());
				if (value < alpha) {
					logDebug("alpha cut-off, value {}, alpha {}", evaluationType, depth, value, alpha);
					moveEntry.addEvaluation(EvalType.ALPHA_CUTOFF);
					line.removeLastMove();
					break; /* alpha cut-off */
				}
				if (value < beta) { // beta = min(beta, value)
					beta = value;
					moveEntry.addEvaluation(EvalType.BESTSOFAR);
					if (result.getLine().isPresent()) {
						currentBestLine = new Line(result.getLine().get());
						if (depth == startDepth) {
							result.printUCI(outputStream);
						}
					}
					logDebug("saved new best line: {}, alpha {}, beta {}", evaluationType, depth, currentBestLine,
							alpha, beta);
				}
				line.removeLastMove();
			}
			// TODO replace this bit with posn.evaluate?
			if (moves.isEmpty()) {
				// test for checkmate or stalemate
				if (posn.isKingInCheck()) {
					logDebug("mate found, currentLine: {}", evaluationType, depth, line);
					// return a higher score for a mate in 3 compared to a mate in 5
					return new SearchResult(MAX_VAL - (10 - depth), line, line.getMoves().size(), startDepth); // need
																												// to
																												// remain
																												// below
																												// MAX_VAL
				} else {
					// statemate: evaluate as 0
					return new SearchResult(0, startDepth, line);
				}
			}
			// is possible to get here without having set 'currentBestLine'
			// e.g. have tried all possibilities but they were all outside of the
			// [alpha,beta] range
			return new SearchResult(value, startDepth, currentBestLine);
		default:
			throw new RuntimeException("unexpected value for MIN/MAX ?");
		}
	}

	/**
	 * Sorts the given move lists according to various heuristics.
	 *
	 * @param posn  the current position
	 * @param moves all available moves
	 * @return a sorted list of available moves (hopefully, better moves first)
	 */
	private List<IMove> orderMoves(Position posn, List<IMove> moves) {

		List<IMove> captures = new ArrayList<>(moves.size());
		List<IMove> nonCaptures = new ArrayList<>(moves.size());
		for (IMove move : moves) {
			if (move.isCapture()) {
				captures.add(move);
			} else {
				nonCaptures.add(move);
			}
		}
		captures = orderCaptures(posn, captures);
		nonCaptures = orderNonCaptures(posn, nonCaptures);

		// ... and return
		captures.addAll(nonCaptures);
		return captures;
	}

	// for captures: Most Valuable Victim - Least Valuable Aggressor or Static
	// Exchange Evaluation (SEE)
	// http://chessprogramming.wikispaces.com/MVV-LVA
	private List<IMove> orderCaptures(Position posn, List<IMove> moves) {
		return moves;
	}

	// for non-captures: history heuristic
	private List<IMove> orderNonCaptures(Position posn, List<IMove> moves) {
		return moves;
	}

	// /*
	// * if 'max' has found a move with evaluation +5, then a further move which
	// evaluates to +3 can be immediately discarded.
	// */
	// public SearchResult alphaBetaMax(Position posn,
	// Line line,
	// int alpha,
	// int beta,
	// int depthleft) {
	// LOG.debug("in alphaBetaMax, line {}, alpha {}, beta {}, depth {}", line,
	// alpha, beta, depthleft);
	// if (depthleft == 0) {
	// int eval = posn.evaluate();
	// NBR_POSNS_EVALUATED++;
	// LOG.debug("evaluated posn: {}, posn\n{}", eval, posn);
	// return new SearchResult(eval, line);
	// }
	// List<Move> moves = posn.findMoves(posn.getSideToMove());
	// Line currentBestLine = null;
	// int currentBestScore = MIN_VAL;
	// for (Move move : moves) {
	// LOG.debug("(max) alpha {}, beta {}, depth {}, move {}", alpha, beta,
	// depthleft, move);
	// Position newPosn = posn.move(move);
	// line.addMove(move);
	// SearchResult result = alphaBetaMin(newPosn, line, alpha, beta, depthleft -
	// 1);
	// if (result.getScore() > currentBestScore) {
	// currentBestScore = result.getScore();
	// if (currentBestScore >= beta) {
	// LOG.debug("depth {}, move {}: beta cutoff {}, {} for posn\n{}", depthleft,
	// move, result, beta, newPosn);
	// // 'undo' move
	// line.removeLastMove();
	// return result;//new SearchResult(beta); // fail hard beta-cutoff
	// }
	// }
	// if (result.getScore() > alpha) {
	// LOG.debug("depth {}, move {}: new alpha {}, {} for posn\n{}", depthleft,
	// move, result, alpha, newPosn);
	// alpha = result.getScore(); // alpha acts like max in MiniMax
	// currentBestLine = new Line(result.getLine().get());
	// // print best line (if at top-level depth ?)
	// if (depthleft == 4) {
	// result.printUCI(outputStream);
	// }
	// }
	// // 'undo' move
	// line.removeLastMove();
	// }
	// if (currentBestScore <= MIN_VAL) {
	// // test for checkmate or stalemate
	// System.out.println("max: test for checkmate or stalemate");
	// if (posn.isInCheck()) {
	// return new SearchResult(currentBestScore + depthleft, line);
	// } else {
	// // statemate: evaluate as 0
	// return new SearchResult(0, line);
	// }
	// }
	// return new SearchResult(currentBestScore, currentBestLine);
	// }

	private String timeTaken(long durationInMs) {
		return String.format("%02d.%02d", durationInMs / 1000, durationInMs % 1000);
	}

	@Override
	public int getCurrentNbrNodesSearched() {
		return NBR_NODES_SEARCHED;
	}

	@Override
	public int getCurrentDepth() {
		return startDepth;
	}

	@Override
	public void incrementDepth(int increment) {
		startDepth += increment;
	}

	enum MiniMax {
		MAX, MIN
	}

	enum EvalType {
		BESTSOFAR, ALPHA_CUTOFF, BETA_CUTOFF, NORMAL;

		public static String print(Set<EvalType> evaluations) {
			StringBuilder sb = new StringBuilder();
			for (EvalType eval : evaluations) {
				if (eval != NORMAL) {
					sb.append(eval).append(" ");
				}
			}
			return sb.toString();
		}
	}

	static class MoveTree {
		private MiniMax type;
		private Set<EvalType> evaluations;
		private IMove move;
		private int score;
		private int depth;
		private int min; // min value at time of evaluation
		private int max;// max value at time of evaluation
		private List<MoveTree> followingMoves;

		public MoveTree(MiniMax type, IMove move, int depth, int min, int max) {
			this.type = type;
			this.move = move;
			this.depth = depth;
			this.min = min;
			this.max = max;
			this.evaluations = new HashSet<>();
			this.evaluations.add(EvalType.NORMAL);
			this.followingMoves = new ArrayList<>();
		}

		public void setScore(int score) {
			this.score = score;
		}

		public void addEvaluation(EvalType evaluation) {
			this.evaluations.add(evaluation);
		}

		public void addEntry(MoveTree moveTree) {
			this.followingMoves.add(moveTree);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(500);
			// ignore dummy first entry
			if (type == null) {
				sb.append("\n");
			} else {
				sb.append(String.format("%s %s %d %s (min:%d max:%d) %d %s%n", type, "        ".substring(0, 9 - depth),
						depth, move, min, max, score, EvalType.print(evaluations)));
			}
			for (MoveTree entry : followingMoves) {
				sb.append(entry.toString());
			}
			return sb.toString();
		}
	}

}
