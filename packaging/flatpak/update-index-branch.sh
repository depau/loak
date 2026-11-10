#!/usr/bin/env bash
# Commit the generated Flatpak OCI index to the long-lived `flatpak-index` branch
# and push it. Flatpak reads `<raw base>/index/static` from that branch, so the
# branch is the feed's stable address; the images themselves live in the registry
# named by the index.
#
# Usage:
#   packaging/flatpak/update-index-branch.sh --index FILE --repository OWNER/REPO \
#       --tag TAG [--branch flatpak-index]
#
# The branch is created as an orphan on first use. The main checkout is left
# alone: the update happens in a throwaway worktree.
set -euo pipefail
trap 'echo "update-index-branch.sh: line $LINENO: \"$BASH_COMMAND\" failed (exit $?)" >&2' ERR

INDEX=""
REPOSITORY=""
TAG=""
BRANCH=flatpak-index
while [ $# -gt 0 ]; do
	case "$1" in
		--index) INDEX="${2:?--index needs a value}"; shift 2 ;;
		--repository) REPOSITORY="${2:?--repository needs a value}"; shift 2 ;;
		--tag) TAG="${2:?--tag needs a value}"; shift 2 ;;
		--branch) BRANCH="${2:?--branch needs a value}"; shift 2 ;;
		-h | --help) sed -n '2,21p' "$0"; exit 0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
done
[ -n "$INDEX" ] || { echo "error: --index is required" >&2; exit 2; }
[ -n "$REPOSITORY" ] || { echo "error: --repository is required" >&2; exit 2; }
[ -n "$TAG" ] || { echo "error: --tag is required" >&2; exit 2; }
[ -f "$INDEX" ] || { echo "error: no index at $INDEX" >&2; exit 1; }

RAW_BASE="https://raw.githubusercontent.com/$REPOSITORY/$BRANCH"
TREE="$(mktemp -d)/tree"
WORKTREE_ADDED=0
cleanup() {
	if [ "$WORKTREE_ADDED" = 1 ]; then
		git worktree remove --force "$TREE" 2>/dev/null || true
	fi
	rm -rf "$(dirname "$TREE")"
}
trap cleanup EXIT

write_flatpakrepo() {
	cat >"$TREE/loak.flatpakrepo" <<EOF
[Flatpak Repo]
Title=Lo'ak
Comment=Official Lo'ak Flatpak releases
Description=Official Flatpak repository for Lo'ak
Homepage=https://github.com/$REPOSITORY
Url=oci+$RAW_BASE
EOF
}

stage() {
	mkdir -p "$TREE/index"
	cp "$INDEX" "$TREE/index/static"
	write_flatpakrepo
	git -C "$TREE" add index/static loak.flatpakrepo
	git -C "$TREE" \
		-c user.name="github-actions[bot]" \
		-c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
		commit -m "chore(flatpak): publish OCI index for $TAG" || true
}

git fetch origin "$BRANCH" 2>/dev/null || true
if git rev-parse --verify --quiet "refs/remotes/origin/$BRANCH" >/dev/null; then
	git worktree add --detach "$TREE" "origin/$BRANCH"
	WORKTREE_ADDED=1
	git -C "$TREE" checkout -B "$BRANCH"
else
	git worktree add --detach "$TREE" HEAD
	WORKTREE_ADDED=1
	git -C "$TREE" checkout --orphan "$BRANCH"
	git -C "$TREE" rm -rf --ignore-unmatch .
fi
stage

for attempt in 1 2 3 4 5; do
	if git -C "$TREE" push origin "HEAD:$BRANCH"; then
		echo "pushed $RAW_BASE/loak.flatpakrepo"
		exit 0
	fi
	git -C "$TREE" fetch origin "$BRANCH" 2>/dev/null || true
	if git -C "$TREE" rev-parse --verify --quiet "refs/remotes/origin/$BRANCH" >/dev/null; then
		if ! git -C "$TREE" rebase "origin/$BRANCH" 2>/dev/null; then
			git -C "$TREE" rebase --abort 2>/dev/null || true
			git -C "$TREE" reset --hard "origin/$BRANCH"
			stage
		fi
	fi
	sleep $((attempt * 2))
done

echo "error: could not push $BRANCH after 5 attempts" >&2
exit 1
