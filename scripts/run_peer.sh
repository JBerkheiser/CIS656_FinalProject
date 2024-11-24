#!/bin/bash

# Change to the script's directory or exit if it fails
cd "$(dirname "$0")" || exit 1

# Define paths
SRC_DIR="../Peer/src/peer"
OUT_DIR="../out/production/Peer"

# Ensure the output directory exists
mkdir -p "$OUT_DIR"

# Check if recompilation is needed
needs_recompile=false
for src_file in "$SRC_DIR"/*.java; do
    class_file="$OUT_DIR/$(basename "${src_file%.java}.class")"
    if [ ! -f "$class_file" ] || [ "$src_file" -nt "$class_file" ]; then
        needs_recompile=true
        break
    fi
done

# Recompile if necessary
if $needs_recompile; then
    javac -d "$OUT_DIR" "$SRC_DIR"/*.java
    if [ $? -ne 0 ]; then
        echo "Compilation failed. Check the paths or syntax in your code."
        exit 1
    fi
else
    echo "Peer client is already compiled and up-to-date."
fi

# Run the Peer client
echo "Running the Peer client..."
java -cp "$OUT_DIR" peer.Peer
if [ $? -ne 0 ]; then
    echo "Failed to start the Peer client. Check your classpath or main class."
    exit 1
fi