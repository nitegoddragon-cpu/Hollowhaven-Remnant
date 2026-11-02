from pathlib import Path
import json, zipfile, sys

root=Path('.')
main=root/'app/src/main/java/com/hollowhaven/remnant/MainActivity.java'
svc=root/'app/src/main/java/com/hollowhaven/remnant/RemnantOverlayService.java'
asset=root/'app/src/main/assets/hollowhaven/pets/forest_spirit'
assert main.exists() and svc.exists(), 'Pet Library Java sources missing'
ms=main.read_text(encoding='utf-8'); ss=svc.read_text(encoding='utf-8')
for needle in ['HOLLOWHAVEN_PET_LIBRARY_V1','PET_LIBRARY_DIR','installBuiltInForestSpirit','selectPet(String id)','deleteSelectedPet()','pending_import_pet_id']:
    assert needle in ms, f'Missing MainActivity contract: {needle}'
assert 'pets/library/' in ss, 'Overlay still points only at legacy imported storage'
meta=asset/'pet.json'; assert meta.exists(), 'Forest Spirit metadata missing'
data=json.loads(meta.read_text())
assert data['id']=='forest_spirit' and data['source_frames']==57 and data['validated'] is True
frames=sorted((asset/'idle').glob('*'))
assert len(frames)==57, f'Expected 57 Forest Spirit frames, got {len(frames)}'
assert all(p.suffix.lower() in {'.png','.webp'} for p in frames)
print('Pet Library QA: PASS')
print('Forest Spirit frames:',len(frames))
