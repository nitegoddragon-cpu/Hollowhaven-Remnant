# Hollowhaven Remnant V7.5 Pet Library build handoff

This bundle is prepared to turn the current V7.4 source build into a Pet Library build.

## Included
- `tools/apply_pet_library_v1_forest.py`: applies the persistent Pet Library migration and built-in Forest Spirit installer during CI after the existing V7.4 project ZIP is extracted.
- `tools/qa_pet_library.py`: verifies the patched Java contracts and the 57-frame Forest Spirit pack.
- `.github/workflows/build-apk-v7-5-pet-library.yml`: reproducible GitHub Actions build workflow for debug APK, release APK, and AAB.
- `.github/assets/forest_spirit_q40.zip.b64`: self-contained compressed Forest Spirit asset pack, encoded as text so the build does not depend on an external download.

## Important
The existing GitHub integration currently rejects write operations with HTTP 403. Therefore these files are a local build handoff, not a claim that the repository has been changed.

## Forest Spirit
The included pack contains 57 animation frames at 192x192, stored as quality-40 WebP to reduce APK size. The original lossless PNG asset bundle is kept separately in the conversation files.

## Intended behavior
- Remnant remains permanent.
- Forest Spirit is installed as a built-in library pet on first launch after the new build.
- Imported pets receive unique IDs under `pets/library/<id>`.
- Selecting another pet does not destroy existing pets.
- Reset to Remnant changes only the active pet.
- Legacy `pets/imported` content is migrated into the library when possible.
- Overlay loading reads the selected library pet.
- User-imported pets can be deleted individually; built-in Remnant and Forest Spirit cannot.
