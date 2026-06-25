#!/bin/bash
set -e

NAME="$1"
if [ -z "$NAME" ]; then
    echo "Usage: $0 <save-name>"
    exit 1
fi

DEST="saves/$NAME"
if [ -d "$DEST" ]; then
    echo "Error: '$DEST' already exists"
    exit 1
fi

mkdir -p "$DEST"
rsync -a --exclude="milestone1/6_extracted_source/" --exclude="milestone4/source/" output/ "$DEST/"
echo "Saved to $DEST"
