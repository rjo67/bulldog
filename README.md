Further chess development, moving on from the 'chess' repo.
e.g. more evaluation work.

## TODOs ?

- rewrite checking for a check; generate psuedo moves and then prune
- store Rays in a byte?
- Separate Move up: (origin, target) can be pre-generated, the pieces involved are the variable part
- this may save time since can store the origin,target pair in the static arrays, also eg for castling

Interesting link: https://www.codeproject.com/Articles/5313417/Worlds-fastest-Bitboard-Chess-Movegenerator



## Overview of commits and performance
date | description | perft (posn6, 5ply: 164.075.551 moves)
---- | ----------- | -----
30.12.23 | new repo 'bulldog'. Code moved back into one project. Focus now moving back to eval / uci | average of 8 iterations: 3847,25 ms (42647,5 moves/ms)
13.11.23 | use bitmaps to calculate legal king moves, esp. check if adjacent to opponent's king |  average of 5 iterations: 3839,20 ms (42736,9 moves/ms)

