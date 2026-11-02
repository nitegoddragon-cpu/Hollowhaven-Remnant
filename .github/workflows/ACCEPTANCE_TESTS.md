# Pet Library V1 acceptance tests

- Legacy pets/imported migrates once to pets/library/imported_pet.
- Reset changes only selected_pet and never deletes library pets.
- Every import reserves one ID and writes to that exact ID.
- Import A followed by import B preserves both.
- Selecting A loads only A.
- Selecting B loads only B.
- Deleting B leaves A intact and activates Remnant.
- Remnant cannot be deleted.
- Invalid pet IDs are rejected.
- Existing ZIP traversal, frame-count, image-size and bitmap checks remain passing.
- App restart preserves selected pet.
- ACTION_RELOAD refreshes the overlay after selection.
