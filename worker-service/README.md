# Worker service

The worker uses the official Stockfish executable through its UCI protocol.
A GUI is not required for this backend integration.

Download Stockfish from the official sources:

- https://stockfishchess.org/download/
- https://github.com/official-stockfish/Stockfish/releases/latest

Make the executable available on `PATH`, or configure its full path with:

```bash
export STOCKFISH_PATH=/absolute/path/to/stockfish
```

Optional analysis depth settings:

```bash
export STOCKFISH_FAST_DEPTH=12
export STOCKFISH_DEEP_DEPTH=20
export STOCKFISH_THREADS=1
export STOCKFISH_HASH_MB=128
```

The worker receives an `AnalysisJob` containing a game ID, FEN, and analysis
tier. `FAST` and `DEEP` select the corresponding configured Stockfish depth.

Stockfish is started as a long-lived process and controlled with UCI commands:
`uci`, `setoption`, `isready`, `position fen`, `go depth`, and `quit`.
