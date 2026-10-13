# Immutable release artifacts

`release-manifest.schema.json` documents the release contract. `release-manifest.example.json` contains deliberately invalid placeholders and cannot be deployed. The generator writes the actual manifest from ECR image digests, source Git SHA and reviewed migration metadata; it refuses an existing output filename. Do not commit filled runtime.env, keys or journey credentials here.

All twelve image URIs must belong to the explicitly selected account/region and `orderflow/<service>` repository. An archive must contain the same manifest, identical SQL hashes, complete deployment/runtime/hook files, and no absolute paths, `..`, symlinks, hardlinks or special files. Archive size is bounded. Deployment never downloads arbitrary executable scripts from the internet. The reviewed workflow source bundles the exact Git commit and the host verifies the transported SHA256 before extraction.

`local-restore-evidence.json` records only the real isolated PostgreSQL restore exercise with synthetic records. Application startup, live data recovery, AWS and deployed end-to-end verification remain NOT RUN. It contains no secrets or data records.
