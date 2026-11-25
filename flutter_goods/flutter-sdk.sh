# Source this file from the platform build script.
select_flutter_sdk() {
  local lock_file="$1"
  local sdk_cache="$2"
  local sdk_override="$3"
  local sdk_url sdk_tag sdk_commit sdk_dir actual_commit
  read -r sdk_url sdk_tag sdk_commit < "$lock_file"
  sdk_dir="${sdk_override:-$sdk_cache/$sdk_tag}"

  if [[ -z "$sdk_override" && ! -e "$sdk_dir" ]]; then
    mkdir -p "$sdk_cache" || return 1
    printf 'Downloading Flutter SDK %s…\n' "$sdk_tag" >&2
    git clone --depth 1 --branch "$sdk_tag" "$sdk_url" "$sdk_dir" >&2 || return 1
  fi

  if [[ ! -x "$sdk_dir/bin/flutter" ]] ||
    ! actual_commit=$(git -C "$sdk_dir" rev-parse --verify HEAD 2>/dev/null) ||
    [[ "$actual_commit" != "$sdk_commit" ]]; then
    printf 'Expected Flutter SDK %s (%s) at %s. Use that checkout or choose a new cache directory.\n' \
      "$sdk_tag" "$sdk_commit" "$sdk_dir" >&2
    return 1
  fi
  (cd "$sdk_dir" && pwd)
}
