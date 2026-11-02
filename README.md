# Hollowhaven Pet Library V1

V2 fixes the ID consistency issue found during review. One import reserves one
ID and uses that same ID for the destination directory and selected_pet.

The patch is fail-closed and refuses to modify source if expected anchors are absent.

This is source work, not a verified APK. The connected GitHub write route is
currently returning HTTP 403, so no repository commit or build claim is made.
