#!/bin/bash

echo "Installing git hooks..."
cp -f tools/pre-push .git/hooks/
chmod +x .git/hooks/pre-push
echo "Hooks installed successfully!"