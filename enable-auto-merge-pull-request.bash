#!/usr/bin/env bash
set -e

PR_NUMBER=${1?No PR specified}

gh pr merge --auto --squash "$PR_NUMBER"
