#!/data/data/com.termux/files/usr/bin/bash
# Push AR project to GitHub from Termux.
# Usage: bash push_ar.sh "commit message"

set -e

REPO_DIR="$HOME/AR"
REMOTE_URL="https://github.com/YOUR_USERNAME/AR.git"   # <-- replace with your repo URL
COMMIT_MSG="${1:-Update AR app}"

cd "$REPO_DIR"

if [ ! -d .git ]; then
  git init
  git remote add origin "$REMOTE_URL"
  git branch -M main
fi

git add -A
git commit -m "$COMMIT_MSG" || echo "Nothing to commit"
git push -u origin main
