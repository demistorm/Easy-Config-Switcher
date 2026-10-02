package win.demistorm.easyconfigswitcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import win.demistorm.easyconfigswitcher.config.PresetDelta;
import win.demistorm.easyconfigswitcher.merge.FlatKeyFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetStoreTest {

    @TempDir
    Path game;

    @BeforeEach
    void setUpBasePreset() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":2\n}\n");
        write(game.resolve("config/nested/deep.toml"), "key = \"base\"\n");
        write(game.resolve("config/old.toml"), "legacy = true\n");
        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Custom\nshadowQuality:0.5\n");

        PresetStore.captureFullTo(game, PresetStore.presetDir(game, "Base"));
    }

    @Test
    void derivedCaptureStoresOnlyDifferences() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                particles:minimal
                resourcePacks:["vanilla","file/Stylized.zip"]
                """);
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":99\n}\n");
        write(game.resolve("config/newfile.properties"), "fresh = yes\n");
        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Custom\nshadowQuality:0.8\n");
        Files.delete(game.resolve("config/old.toml"));

        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        Path perfDir = PresetStore.presetDir(game, "Performance");
        LinkedHashMap<String, String> overrideOptions = options(perfDir.resolve("options.txt"));
        assertEquals(Map.of("renderDistance", "9", "particles", "minimal"), overrideOptions);

        PresetDelta delta = PresetStore.loadDelta(game, "Performance");
        assertNotNull(delta);
        assertEquals("Base", delta.base);
        assertTrue(delta.listDeltas.get("resourcePacks").added.contains("file/Stylized.zip"));
        assertTrue(delta.listDeltas.get("resourcePacks").removed.isEmpty());
        assertTrue(delta.deletedFiles.contains("config/old.toml"));
        assertTrue(delta.ancestorHashes.containsKey("config/sodium.json"));

        assertEquals("{\n\"a\":1,\n\"b\":99\n}\n", read(perfDir.resolve("config/sodium.json")));
        assertEquals("{\n\"a\":1,\n\"b\":2\n}\n", read(perfDir.resolve(".base/config/sodium.json")));
        assertTrue(Files.exists(perfDir.resolve("config/newfile.properties")));
        assertFalse(Files.exists(perfDir.resolve(".base/config/newfile.properties")));

        assertEquals(Map.of("shadowQuality", "0.8"), options(perfDir.resolve("shaderpacks/Mellow Shader.zip.txt")));
    }

    @Test
    void applyResolvesBasePlusDelta() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla","file/Stylized.zip"]
                """);
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":99\n}\n");
        write(game.resolve("config/newfile.properties"), "fresh = yes\n");
        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Custom\nshadowQuality:0.8\n");
        Files.delete(game.resolve("config/old.toml"));

        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Performance", out);

        LinkedHashMap<String, String> options = options(out.resolve("options.txt"));
        assertEquals("4120", options.get("version"));
        assertEquals("9", options.get("renderDistance"));
        assertEquals("3", options.get("guiScale"));
        assertEquals("[\"vanilla\",\"file/Stylized.zip\"]", options.get("resourcePacks"));

        assertEquals(1, jsonInt(out.resolve("config/sodium.json"), "a"));
        assertEquals(99, jsonInt(out.resolve("config/sodium.json"), "b"));
        assertEquals("key = \"base\"\n", read(out.resolve("config/nested/deep.toml")));
        assertTrue(Files.exists(out.resolve("config/newfile.properties")));
        assertFalse(Files.exists(out.resolve("config/old.toml")));

        LinkedHashMap<String, String> shaders = options(out.resolve("shaderpacks/Mellow Shader.zip.txt"));
        assertEquals("Custom", shaders.get("profile"));
        assertEquals("0.8", shaders.get("shadowQuality"));
    }

    @Test
    void applyMergesShaderKeyOverrides() throws IOException {
        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Custom\nshadowQuality:0.8\n");
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        write(PresetStore.presetDir(game, "Base").resolve("shaderpacks/Mellow Shader.zip.txt"),
                "profile:Default\nshadowQuality:0.5\n");

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Performance", out);

        LinkedHashMap<String, String> merged = options(out.resolve("shaderpacks/Mellow Shader.zip.txt"));
        assertEquals("Default", merged.get("profile"));
        assertEquals("0.8", merged.get("shadowQuality"));
    }

    @Test
    void baseUpdatesPropagateThroughThreeWayMerge() throws IOException {
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":99\n}\n");
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.scalarChanges.put("guiScale", "4");
        cs.fileChanges.put("config/sodium.json", "{\n\"a\":2,\n\"b\":2\n}\n".getBytes());
        PresetStore.updateBase(game, "Base", cs);

        Path out = game.resolve("out");
        PresetStore.MergeStats stats = PresetStore.applyWithStats(game, "Performance", out);

        assertEquals(0, stats.conflicts);
        assertEquals("4", options(out.resolve("options.txt")).get("guiScale"));
        assertEquals(2, jsonInt(out.resolve("config/sodium.json"), "a"));
        assertEquals(99, jsonInt(out.resolve("config/sodium.json"), "b"));
    }

    @Test
    void conflictingLineTheirsWins() throws IOException {
        write(game.resolve("config/sodium.json"), "{\n\"a\":5,\n\"b\":2\n}\n");
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        write(PresetStore.presetDir(game, "Base").resolve("config/sodium.json"), "{\n\"a\":7,\n\"b\":2\n}\n");

        Path out = game.resolve("out");
        PresetStore.MergeStats stats = PresetStore.applyWithStats(game, "Performance", out);

        assertEquals(0, stats.conflicts);
        assertEquals(5, jsonInt(out.resolve("config/sodium.json"), "a"));
        assertEquals(2, jsonInt(out.resolve("config/sodium.json"), "b"));
    }

    @Test
    void baseResourcePackAdditionsFlowIntoDerived() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla","file/Stylized.zip"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.listChanges.put("resourcePacks", "[\"vanilla\",\"file/3D Items.zip\"]");
        PresetStore.updateBase(game, "Base", cs);

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Performance", out);

        String merged = options(out.resolve("options.txt")).get("resourcePacks");
        List<String> packs = win.demistorm.easyconfigswitcher.merge.ListKeys.parse(merged);
        assertTrue(packs.contains("vanilla"));
        assertTrue(packs.contains("file/3D Items.zip"));
        assertTrue(packs.contains("file/Stylized.zip"));
        assertFalse(packs.contains("file/Realistic.zip"));
    }

    @Test
    void reorderedPackListBecomesOrderedOverride() throws IOException {
        write(PresetStore.presetDir(game, "Base").resolve("options.txt"),
                "version:4120\nrenderDistance:12\nguiScale:3\nresourcePacks:[\"vanilla\",\"file/X.zip\"]\n");
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["file/X.zip","vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetDelta delta = PresetStore.loadDelta(game, "Performance");
        assertNotNull(delta.listDeltas.get("resourcePacks").orderedOverride);
        assertEquals(List.of("file/X.zip", "vanilla"), delta.listDeltas.get("resourcePacks").orderedOverride);
    }

    @Test
    void updateDerivedDropsOverridesMatchingBase() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":99\n}\n");
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.scalarChanges.put("renderDistance", "12");
        cs.fileChanges.put("config/sodium.json", "{\n\"a\":1,\n\"b\":2\n}\n".getBytes());
        PresetStore.updateDerived(game, "Performance", cs);

        Path perfDir = PresetStore.presetDir(game, "Performance");
        assertFalse(Files.exists(perfDir.resolve("options.txt")));
        assertFalse(Files.exists(perfDir.resolve("config/sodium.json")));
        assertFalse(Files.exists(perfDir.resolve(".base/config/sodium.json")));
    }

    @Test
    void updateDerivedRecordsNewOverrides() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.scalarChanges.put("renderDistance", "7");
        cs.scalarChanges.put("maxFps", "120");
        PresetStore.updateDerived(game, "Performance", cs);

        LinkedHashMap<String, String> overrides = options(PresetStore.presetDir(game, "Performance").resolve("options.txt"));
        assertEquals(Map.of("renderDistance", "7", "maxFps", "120"), overrides);
    }

    @Test
    void pruneDropsOverridesRedundantWithNewBase() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.scalarChanges.put("renderDistance", "9");
        PresetStore.updateBase(game, "Base", cs);
        PresetStore.pruneRedundantOverrides(game, "Performance");

        assertFalse(Files.exists(PresetStore.presetDir(game, "Performance").resolve("options.txt")));
    }

    @Test
    void sessionChangesDetectedAgainstReference() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        Path reference = game.resolve("ref");
        PresetStore.applyTo(game, "Performance", reference);

        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:4
                resourcePacks:["vanilla"]
                """);
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":123\n}\n");

        PresetStore.ChangeSet cs = PresetStore.computeSessionChanges(game, reference);
        assertEquals(Map.of("guiScale", "4"), cs.scalarChanges);
        assertTrue(cs.listChanges.isEmpty());
        assertTrue(cs.fileChanges.containsKey("config/sodium.json"));
        assertTrue(cs.deletedFiles.isEmpty());
    }

    @Test
    void promoteToBaseConvertsOthersToDeltas() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:4
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureFullTo(game, PresetStore.presetDir(game, "Fancy"));

        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla"]
                """);

        PresetStore.promoteToBase(game, "Base", List.of("Fancy"));

        assertTrue(PresetStore.isDerived(game, "Fancy"));
        PresetDelta delta = PresetStore.loadDelta(game, "Fancy");
        assertEquals("Base", delta.base);
        assertEquals(Map.of("guiScale", "4"), options(PresetStore.presetDir(game, "Fancy").resolve("options.txt")));

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Fancy", out);
        assertEquals("4", options(out.resolve("options.txt")).get("guiScale"));
        assertEquals("12", options(out.resolve("options.txt")).get("renderDistance"));
    }

    @Test
    void demoteAppliesDerivedAsStandalone() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.demoteBase(game, List.of("Performance"));

        assertFalse(PresetStore.isDerived(game, "Performance"));
        LinkedHashMap<String, String> options = options(PresetStore.presetDir(game, "Performance").resolve("options.txt"));
        assertEquals("9", options.get("renderDistance"));
        assertEquals("3", options.get("guiScale"));
        assertEquals("4120", options.get("version"));
    }

    @Test
    void basePackOrderReachesNonOverridingDerivedVerbatim() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        String orderedList = "[\"vanilla\",\"file/FreshAnimations_v1.10.5.zip\",\"file/FA Entities Expansion.zip\"]";
        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.listChanges.put("resourcePacks", orderedList);
        PresetStore.updateBase(game, "Base", cs);

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Performance", out);
        assertEquals(orderedList, options(out.resolve("options.txt")).get("resourcePacks"));
    }

    @Test
    void basePackOrderPreservedAlongsideDerivedAddedPacks() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla","file/Stylized.zip"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        PresetStore.ChangeSet cs = new PresetStore.ChangeSet();
        cs.listChanges.put("resourcePacks",
                "[\"vanilla\",\"file/FreshAnimations_v1.10.5.zip\",\"file/FA Entities Expansion.zip\"]");
        PresetStore.updateBase(game, "Base", cs);

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Performance", out);
        List<String> packs = win.demistorm.easyconfigswitcher.merge.ListKeys.parse(
                options(out.resolve("options.txt")).get("resourcePacks"));
        assertEquals(List.of("vanilla",
                "file/FreshAnimations_v1.10.5.zip",
                "file/FA Entities Expansion.zip",
                "file/Stylized.zip"), packs);
    }

    @Test
    void baseUpdateWhileOnDerivedMergesOnlySessionChanges() throws IOException {
        String chicken = "{\n  \"entities\": {\n    \"chicken\": \"E1\"\n  }\n}\n";
        String chickenCow = "{\n  \"entities\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\"\n  }\n}\n";
        String chickenCowPig = "{\n  \"entities\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\",\n    \"pig\": \"E3\"\n  }\n}\n";

        write(game.resolve("config/vh.json"), chicken);
        write(PresetStore.presetDir(game, "Base").resolve("config/vh.json"), chicken);

        write(game.resolve("config/vh.json"), chickenCow);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        write(game.resolve("config/vh.json"), chickenCowPig);
        Path ref = game.resolve("ref");
        PresetStore.applyTo(game, "Performance", ref);
        PresetStore.ChangeSet cs = PresetStore.computeSessionChanges(game, ref);
        PresetStore.updateBase(game, "Base", cs);

        String baseVh = read(PresetStore.presetDir(game, "Base").resolve("config/vh.json"));
        assertTrue(baseVh.contains("chicken"));
        assertTrue(baseVh.contains("pig"));
        assertFalse(baseVh.contains("cow"));

        Path out = game.resolve("out");
        PresetStore.MergeStats stats = PresetStore.applyWithStats(game, "Performance", out);
        String outVh = read(out.resolve("config/vh.json"));
        assertTrue(outVh.contains("chicken"));
        assertTrue(outVh.contains("cow"));
        assertTrue(outVh.contains("pig"));
    }

    @Test
    void baseListUpdateWhileOnDerivedAddsOnlySessionPacks() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla","file/Bare Bones.zip"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla","file/Bare Bones.zip","file/Dramatic Skies.zip"]
                """);
        Path ref = game.resolve("ref");
        PresetStore.applyTo(game, "Performance", ref);
        PresetStore.ChangeSet cs = PresetStore.computeSessionChanges(game, ref);
        PresetStore.updateBase(game, "Base", cs);

        List<String> basePacks = win.demistorm.easyconfigswitcher.merge.ListKeys.parse(presetOptions("Base").get("resourcePacks"));
        assertEquals(List.of("vanilla", "file/Dramatic Skies.zip"), basePacks);

        Path out = game.resolve("out");
        PresetStore.applyTo(game, "Performance", out);
        List<String> perfPacks = win.demistorm.easyconfigswitcher.merge.ListKeys.parse(options(out.resolve("options.txt")).get("resourcePacks"));
        assertEquals(List.of("vanilla", "file/Dramatic Skies.zip", "file/Bare Bones.zip"), perfPacks);
    }

    @Test
    void crossPresetFileUpdateKeepsOverridesAndAppliesSessionChange() throws IOException {
        String chicken = "{\n  \"entities\": {\n    \"chicken\": \"E1\"\n  }\n}\n";
        String chickenCow = "{\n  \"entities\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\"\n  }\n}\n";
        String chickenNew = "{\n  \"entities\": {\n    \"chicken\": \"E9\"\n  }\n}\n";

        write(game.resolve("config/vh.json"), chicken);
        write(PresetStore.presetDir(game, "Base").resolve("config/vh.json"), chicken);
        write(game.resolve("config/vh.json"), chickenCow);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        write(game.resolve("config/vh.json"), chickenNew);
        Path ref = game.resolve("ref");
        PresetStore.applyTo(game, "Base", ref);
        Path target = game.resolve("ref-target");
        PresetStore.applyTo(game, "Performance", target);

        PresetStore.ChangeSet cs = PresetStore.computeSessionChanges(game, ref);
        PresetStore.updateDerived(game, "Performance", cs, ref, target);

        String override = read(PresetStore.presetDir(game, "Performance").resolve("config/vh.json"));
        assertTrue(override.contains("E9"));
        assertTrue(override.contains("cow"));
    }

    @Test
    void crossPresetShaderUpdateKeepsProfileAndAppliesSessionKey() throws IOException {
        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Default\nshadowQuality:0.5\n");
        write(PresetStore.presetDir(game, "Base").resolve("shaderpacks/Mellow Shader.zip.txt"),
                "profile:Default\nshadowQuality:0.5\n");
        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Custom\nshadowQuality:0.5\n");
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");

        write(game.resolve("shaderpacks/Mellow Shader.zip.txt"), "profile:Default\nshadowQuality:0.9\n");
        Path ref = game.resolve("ref");
        PresetStore.applyTo(game, "Base", ref);
        Path target = game.resolve("ref-target");
        PresetStore.applyTo(game, "Performance", target);

        PresetStore.ChangeSet cs = PresetStore.computeSessionChanges(game, ref);
        PresetStore.updateDerived(game, "Performance", cs, ref, target);

        LinkedHashMap<String, String> partial = options(
                PresetStore.presetDir(game, "Performance").resolve("shaderpacks/Mellow Shader.zip.txt"));
        assertEquals("Custom", partial.get("profile"));
        assertEquals("0.9", partial.get("shadowQuality"));
    }

    private LinkedHashMap<String, String> presetOptions(String name) throws IOException {
        return options(PresetStore.presetDir(game, name).resolve("options.txt"));
    }

    private int jsonInt(Path file, String key) throws IOException {
        return com.google.gson.JsonParser.parseString(read(file)).getAsJsonObject().get(key).getAsInt();
    }

    private LinkedHashMap<String, String> options(Path file) throws IOException {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        return FlatKeyFile.parse(Files.readAllLines(file));
    }

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String read(Path file) throws IOException {
        return Files.readString(file);
    }
}
