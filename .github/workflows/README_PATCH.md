# Hollowhaven 2.6.0 secure bridge patch

This patch fixes the phone AI path so the Android app talks to the secure Railway Clip Forge bridge instead of putting a WaveSpeed API key on the phone.

## Android path
Remnant reference + driving video -> Railway Clip Forge `/api/generate` -> WaveSpeed Wan 2.2 Animate 2 -> `/api/result/:id` -> MP4 -> phone frame importer.

The default bridge is:
`https://hollowhaven-clip-forge-production.up.railway.app`

The Android client no longer supports `wavespeed://API_KEY`.

## Server hardening
- Uses the OS temporary directory instead of a repo-relative `tmp` folder.
- Streams media to the WaveSpeed signed upload URL instead of reading the whole file into RAM.
- Rejects non-image/non-video uploads.
- Limits each uploaded input to 250 MB.
- Keeps `WAVESPEED_API_KEY` server-side only.

## Current verification
Railway production service is online and its latest deployment is SUCCESS. The actual end-to-end generation request still needs a live network POST and a real WaveSpeed result. The current execution environment cannot resolve the Railway public hostname, and the connected Opera browser is not currently attached, so this patch does not claim a completed AI render.
