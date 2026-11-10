#!/usr/bin/env python3
"""Merge one Flatpak OCI image into a static OCI index that Flatpak reads.

Flatpak OCI remotes (an `oci+https://...` URL) fetch `<URL>/index/static` first.
That file lists, per image, the registry and digest to pull. `flatpak build-bundle
--oci` writes a single-image OCI layout; this script turns that layout's manifest
and config into the corresponding index entry, keeping the registry and any other
images already in the file (one entry per architecture).

The index is what makes the registry movable: the images live at
`<Registry>/v2/<Name>/manifests/<Digest>`, so changing GHCR ownership only
requires a new index, not new Flatpak remotes.
"""

import argparse
import json
import pathlib
import sys

REQUIRED_LABELS = ("org.flatpak.ref", "org.flatpak.metadata")
APPSTREAM_PREFIX = "org.freedesktop.appstream."


def read_json(path: pathlib.Path) -> dict:
	try:
		return json.loads(path.read_text())
	except (OSError, json.JSONDecodeError) as error:
		raise SystemExit(f"error: cannot read {path}: {error}") from error


def read_oci_layout(oci_dir: pathlib.Path) -> tuple[str, dict, dict]:
	layout = read_json(oci_dir / "index.json")
	manifests = layout.get("manifests") or []
	if len(manifests) != 1:
		raise SystemExit(
			f"error: {oci_dir} holds {len(manifests)} images; expected exactly one"
		)
	manifest_digest = manifests[0].get("digest", "")
	if not manifest_digest.startswith("sha256:"):
		raise SystemExit(f"error: {oci_dir}: unsupported manifest digest {manifest_digest!r}")

	manifest = read_json(oci_dir / "blobs" / "sha256" / manifest_digest.split(":", 1)[1])
	config_digest = manifest.get("config", {}).get("digest", "")
	if not config_digest.startswith("sha256:"):
		raise SystemExit(f"error: {oci_dir}: manifest has no config digest")
	config = read_json(oci_dir / "blobs" / "sha256" / config_digest.split(":", 1)[1])
	return manifest_digest, manifest, config


def make_image(
	digest: str, manifest: dict, config: dict, tags: list[str]
) -> dict:
	labels = (config.get("config") or {}).get("Labels") or {}
	missing = [name for name in REQUIRED_LABELS if not labels.get(name)]
	if missing:
		raise SystemExit(
			"error: the OCI image lacks Flatpak labels: " + ", ".join(missing)
		)

	if not any(name.startswith(APPSTREAM_PREFIX) for name in labels):
		print(
			"warning: the image has no AppStream labels; "
			"Flatpak will list it without the app's metadata or icon",
			file=sys.stderr,
		)

	image = {
		"Architecture": config.get("architecture", ""),
		"Digest": digest,
		"Labels": {
			name: value
			for name, value in labels.items()
			if name.startswith(("org.flatpak.", APPSTREAM_PREFIX))
		},
		"MediaType": manifest.get(
			"mediaType", "application/vnd.oci.image.manifest.v1+json"
		),
		"OS": config.get("os", "linux"),
	}
	if tags:
		image["Tags"] = tags
	if not image["Architecture"]:
		raise SystemExit("error: the OCI image config has no architecture")
	return image


def main() -> None:
	parser = argparse.ArgumentParser(description=__doc__)
	parser.add_argument(
		"--oci-dir", type=pathlib.Path, required=True, help="single-image OCI layout"
	)
	parser.add_argument(
		"--index", type=pathlib.Path, required=True, help="index/static to update"
	)
	parser.add_argument(
		"--repository", required=True, help="registry repository, e.g. depau/loak"
	)
	parser.add_argument(
		"--registry",
		default="ghcr.io",
		help="registry host used when creating a fresh index (default: ghcr.io)",
	)
	parser.add_argument(
		"--tags", nargs="*", default=[], help="tags to associate with the image"
	)
	args = parser.parse_args()

	digest, manifest, config = read_oci_layout(args.oci_dir)
	image = make_image(digest, manifest, config, args.tags)

	if args.index.exists():
		index = read_json(args.index)
		if not isinstance(index.get("Results"), list):
			raise SystemExit(f"error: {args.index} is not a Flatpak OCI index")
	else:
		index = {"Registry": f"https://{args.registry}", "Results": []}
	index.setdefault("Registry", f"https://{args.registry}")

	results = index["Results"]
	result = next(
		(entry for entry in results if entry.get("Name") == args.repository), None
	)
	if result is None:
		result = {"Name": args.repository, "Images": []}
		results.append(result)
	images = result.setdefault("Images", [])

	ref = image["Labels"]["org.flatpak.ref"]
	images[:] = [
		existing
		for existing in images
		if not (
			existing.get("Architecture") == image["Architecture"]
			and (existing.get("Labels") or {}).get("org.flatpak.ref") == ref
		)
	]
	images.append(image)
	images.sort(
		key=lambda entry: (
			entry.get("Architecture", ""),
			(entry.get("Labels") or {}).get("org.flatpak.ref", ""),
		)
	)
	results.sort(key=lambda entry: entry.get("Name", ""))

	args.index.parent.mkdir(parents=True, exist_ok=True)
	args.index.write_text(json.dumps(index, indent=2) + "\n")
	print(f"indexed {args.repository}:{image['Architecture']} {digest} in {args.index}")


if __name__ == "__main__":
	main()
