#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "usage: $0 <base-sha> <head-sha>" >&2
  exit 2
fi

base_sha="$1"
head_sha="$2"

git cat-file -e "${base_sha}^{commit}"
git cat-file -e "${head_sha}^{commit}"

has_changes=false
run_android=false

while IFS= read -r -d '' path; do
  has_changes=true
  case "$path" in
    *.md | .opencodereview/* | .github/OPEN_CODE_REVIEW.md | \
      .github/workflows/open-code-review.yml | \
      .github/workflows/open-code-review-history.yml)
      echo "Android CI skip-safe path: $path" >&2
      ;;
    *)
      echo "Android CI relevant or unknown path: $path" >&2
      run_android=true
      break
      ;;
  esac
done < <(git diff --name-only -z "$base_sha" "$head_sha")

if [[ "$has_changes" != "true" ]]; then
  echo "unable to classify an empty change range" >&2
  exit 1
fi

echo "$run_android"
