#!/usr/bin/env python3
import sys
from pathlib import Path

MARKER = "HOLLOWHAVEN_PET_LIBRARY_V1"

def require(s, needle, label):
    if needle not in s:
        raise SystemExit(f"PATCH BLOCKED: {label}")

def once(s, old, new, label):
    require(s, old, label)
    return s.replace(old, new, 1)

def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: apply_pet_library_v1.py MainActivity.java RemnantOverlayService.java")

    main_path = Path(sys.argv[1])
    service_path = Path(sys.argv[2])
    main = main_path.read_text(encoding="utf-8")
    service = service_path.read_text(encoding="utf-8")

    if MARKER in main or MARKER in service:
        raise SystemExit("PATCH BLOCKED: Pet Library V1 marker already exists")

    marker = "    private static final int MAX_PET_IMAGE = 320;\n"
    additions = r"""
    private static final String HOLLOWHAVEN_PET_LIBRARY_V1 = "HOLLOWHAVEN_PET_LIBRARY_V1";
    private static final String PET_REMNANT = "remnant";
    private static final String PET_LIBRARY_DIR = "pets/library";
    private static final String LEGACY_IMPORTED_DIR = "pets/imported";

    private File petLibraryRoot() {
        return new File(getFilesDir(), PET_LIBRARY_DIR);
    }

    private File petDir(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,48}")) return null;
        return new File(petLibraryRoot(), id);
    }

    private String newPetId() {
        return "pet_" + System.currentTimeMillis();
    }

    private String prepareImportPet() {
        ensurePetLibrary();
        String id = newPetId();
        prefs.edit().putString("pending_import_pet_id", id).apply();
        return id;
    }

    private String pendingImportPetId() {
        String id = prefs.getString("pending_import_pet_id", "");
        if (!id.matches("[a-zA-Z0-9_-]{1,48}")) id = prepareImportPet();
        return id;
    }

    private String activePetId() {
        String id = prefs.getString("selected_pet", PET_REMNANT);
        return id == null || id.trim().isEmpty() ? PET_REMNANT : id;
    }

    private void ensurePetLibrary() {
        File root = petLibraryRoot();
        if (!root.isDirectory()) root.mkdirs();

        File legacy = new File(getFilesDir(), LEGACY_IMPORTED_DIR);
        File migrated = petDir("imported_pet");
        if (legacy.isDirectory() && migrated != null && !migrated.exists()) {
            File[] children = legacy.listFiles();
            if (children != null && children.length > 0) {
                migrated.getParentFile().mkdirs();
                if (legacy.renameTo(migrated)) {
                    prefs.edit()
                        .putString("pet_imported_pet_name", "Imported Pet")
                        .putString("pet_imported_pet_source",
                            prefs.getString("import_source", "legacy"))
                        .apply();
                }
            }
        }

        if (!prefs.contains("selected_pet")) {
            prefs.edit()
                .putString("selected_pet", PET_REMNANT)
                .putBoolean(HOLLOWHAVEN_PET_LIBRARY_V1, true)
                .apply();
        }
    }

    private void selectPet(String id) {
        ensurePetLibrary();
        if (PET_REMNANT.equals(id)) {
            prefs.edit().putString("selected_pet", PET_REMNANT).apply();
        } else {
            File dir = petDir(id);
            if (dir == null || !dir.isDirectory()) {
                toast("That pet is no longer available.");
                return;
            }
            prefs.edit().putString("selected_pet", id).apply();
        }
        reloadRunningPet();
        notifyWebImported(importedCount());
    }

    private void deleteSelectedPet() {
        ensurePetLibrary();
        String id = activePetId();
        if (PET_REMNANT.equals(id)) {
            toast("The built-in Remnant cannot be deleted.");
            return;
        }
        File dir = petDir(id);
        if (dir != null) deleteTree(dir);
        prefs.edit().putString("selected_pet", PET_REMNANT).apply();
        reloadRunningPet();
        notifyWebImported(0);
        toast("Pet removed. Remnant is active.");
    }

"""
    main = once(main, marker, marker + additions, "MAX_PET_IMAGE anchor")

    old_reset = """    private void resetToRemnant() {
        try {
            File root = new File(getFilesDir(), "pets/imported");
            deleteTree(root);
            prefs.edit().putString("selected_pet", "remnant").putInt("imported_count", 0).putInt("import_fps", 12).remove("import_source").apply();
            reloadRunningPet();
            toast("The original Remnant is active again.");
            notifyWebImported(0);
        } catch (Throwable e) { toast("Could not reset the pet library safely."); }
    }
"""
    new_reset = """    private void resetToRemnant() {
        try {
            ensurePetLibrary();
            prefs.edit().putString("selected_pet", PET_REMNANT).apply();
            reloadRunningPet();
            toast("The original Remnant is active again. Your other pets were kept.");
            notifyWebImported(0);
        } catch (Throwable e) {
            toast("Could not switch back to the Remnant safely.");
        }
    }
"""
    main = once(main, old_reset, new_reset, "destructive resetToRemnant implementation")

    old_save = """    private void saveImported(int count, int fps, String source) {
        prefs.edit().putString("selected_pet", count > 0 ? "imported" : "remnant")
                .putInt("imported_count", count).putInt("import_fps", fps).putString("import_source", source).apply();
        reloadRunningPet();
"""
    new_save = """    private void saveImported(int count, int fps, String source) {
        if (count <= 0) {
            prefs.edit().putString("selected_pet", PET_REMNANT)
                    .remove("pending_import_pet_id").apply();
            reloadRunningPet();
            return;
        }
        ensurePetLibrary();
        String id = pendingImportPetId();
        prefs.edit().putString("selected_pet", id)
                .putInt("imported_count", count)
                .putInt("import_fps", fps)
                .putString("import_source", source)
                .putBoolean(HOLLOWHAVEN_PET_LIBRARY_V1, true)
                .remove("pending_import_pet_id")
                .apply();
        reloadRunningPet();
"""
    main = once(main, old_save, new_save, "saveImported implementation")

    for signature in (
        "    private void importZip(Uri zip) {\n",
        "    private void importFolder(Uri tree) {\n",
        "    private void importVideo(Uri video) {\n",
    ):
        if signature in main:
            main = main.replace(signature, signature + "        prepareImportPet();\n", 1)

    old_dest = 'new File(getFilesDir(), "pets/imported")'
    if old_dest not in main:
        raise SystemExit("PATCH BLOCKED: legacy import destination not found")
    main = main.replace(old_dest, 'new File(getFilesDir(), PET_LIBRARY_DIR + "/" + pendingImportPetId())')

    bridge = '        @JavascriptInterface public void resetToRemnant() { MainActivity.this.resetToRemnant(); }\n'
    if bridge in main:
        main = main.replace(
            bridge,
            bridge +
            '        @JavascriptInterface public void selectPet(String id) { MainActivity.this.selectPet(id); }\n' +
            '        @JavascriptInterface public void deleteSelectedPet() { MainActivity.this.deleteSelectedPet(); }\n' +
            '        @JavascriptInterface public String getSelectedPet() { ensurePetLibrary(); return activePetId(); }\n',
            1
        )

    old_loader = """        if(prefs==null||!"imported".equals(prefs.getString("selected_pet","remnant")))return;
        File dir=new File(getFilesDir(),"pets/imported");
"""
    new_loader = """        if(prefs==null)return;
        String selected = prefs.getString("selected_pet","remnant");
        if ("remnant".equals(selected) || selected == null || selected.trim().isEmpty()) return;
        if (!selected.matches("[a-zA-Z0-9_-]{1,48}")) return;
        File dir=new File(getFilesDir(),"pets/library/" + selected);
"""
    service = once(service, old_loader, new_loader, "overlay legacy imported loader")

    main_path.write_text(main, encoding="utf-8")
    service_path.write_text(service, encoding="utf-8")
    print("Pet Library V1 patch applied successfully.")

if __name__ == "__main__":
    main()
