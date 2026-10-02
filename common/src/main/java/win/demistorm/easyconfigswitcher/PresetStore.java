package win.demistorm.easyconfigswitcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;
import win.demistorm.easyconfigswitcher.config.PresetDelta;
import win.demistorm.easyconfigswitcher.merge.Diff3;
import win.demistorm.easyconfigswitcher.merge.FlatKeyFile;
import win.demistorm.easyconfigswitcher.merge.JavaDiffUtilsLineDiffer;
import win.demistorm.easyconfigswitcher.merge.JsonMerge;
import win.demistorm.easyconfigswitcher.merge.LineDiffer;
import win.demistorm.easyconfigswitcher.merge.ListKeys;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PresetStore {

    public static final class MergeStats {
        public int conflicts;
        public int textMerged;

        @Override
        public String toString() {
            return textMerged + " files merged, " + conflicts + " conflicts (preset won)";
        }
    }

    public static final class ChangeSet {
        public final Map<String, String> scalarChanges = new LinkedHashMap<>();
        public final Map<String, String> listChanges = new LinkedHashMap<>();
        public final Map<String, byte[]> fileChanges = new LinkedHashMap<>();
        public final Map<String, byte[]> refFiles = new LinkedHashMap<>();
        public final Map<String, String> refListValues = new LinkedHashMap<>();
        public final List<String> deletedFiles = new ArrayList<>();

        public boolean isEmpty() {
            return scalarChanges.isEmpty() && listChanges.isEmpty() && fileChanges.isEmpty() && deletedFiles.isEmpty();
        }

        public String summary() {
            int options = scalarChanges.size() + listChanges.size();
            return options + " option" + (options == 1 ? "" : "s")
                    + ", " + fileChanges.size() + " file" + (fileChanges.size() == 1 ? "" : "s")
                    + (deletedFiles.isEmpty() ? "" : ", " + deletedFiles.size() + " deletion" + (deletedFiles.size() == 1 ? "" : "s"));
        }
    }

    private record SourceRoots(Path options, Path config, Path shaderpacks) {
    }

    private static final Logger LOGGER = EasyConfigSwitcher.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LineDiffer DIFFER = new JavaDiffUtilsLineDiffer();
    private static final String DELTA_FILE = "delta.json";
    private static final String ANCESTOR_DIR = ".base";

    private PresetStore() {
    }

    public static Path ecsDir(Path game) {
        return game.resolve("ecs-presets");
    }

    public static Path presetsDir(Path game) {
        return ecsDir(game).resolve("presets");
    }

    public static Path presetDir(Path game, String name) {
        return presetsDir(game).resolve(name);
    }

    public static Path preRestoreDir(Path game) {
        return ecsDir(game).resolve(".pre-restore");
    }

    public static Path stagingDir(Path game) {
        return ecsDir(game).resolve(".staging");
    }

    public static boolean isDerived(Path game, String name) {
        return Files.exists(presetDir(game, name).resolve(DELTA_FILE));
    }

    public static PresetDelta loadDelta(Path game, String name) {
        Path file = presetDir(game, name).resolve(DELTA_FILE);
        if (!Files.exists(file)) return null;
        try {
            PresetDelta delta = GSON.fromJson(Files.readString(file), PresetDelta.class);
            if (delta == null) return null;
            if (delta.deletedFiles == null) delta.deletedFiles = new ArrayList<>();
            if (delta.listDeltas == null) delta.listDeltas = new LinkedHashMap<>();
            if (delta.ancestorHashes == null) delta.ancestorHashes = new LinkedHashMap<>();
            for (Map.Entry<String, PresetDelta.ListDelta> entry : delta.listDeltas.entrySet()) {
                PresetDelta.ListDelta ld = entry.getValue();
                if (ld == null) {
                    ld = new PresetDelta.ListDelta();
                    entry.setValue(ld);
                }
                if (ld.added == null) ld.added = new ArrayList<>();
                if (ld.removed == null) ld.removed = new ArrayList<>();
            }
            return delta;
        } catch (IOException e) {
            LOGGER.error("Failed to read delta file for preset '{}'", name, e);
            return null;
        }
    }

    public static String describeDelta(Path game, String name) {
        PresetDelta delta = loadDelta(game, name);
        if (delta == null) return null;
        int optionOverrides = 0;
        Path overrideOptions = presetDir(game, name).resolve("options.txt");
        if (Files.exists(overrideOptions)) {
            try {
                optionOverrides = FlatKeyFile.parse(Files.readAllLines(overrideOptions, StandardCharsets.UTF_8)).size();
            } catch (IOException ignored) {
            }
        }
        int fileOverrides = 0;
        Path overrideConfig = presetDir(game, name).resolve("config");
        Path overrideShaders = presetDir(game, name).resolve("shaderpacks");
        try {
            if (Files.isDirectory(overrideConfig)) {
                fileOverrides += walkRel(overrideConfig).size();
            }
            if (Files.isDirectory(overrideShaders)) {
                fileOverrides += walkRel(overrideShaders).size();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to count overrides for preset '{}'", name, e);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Base: ").append(delta.base);
        sb.append(" · ").append(optionOverrides).append(" option override").append(optionOverrides == 1 ? "" : "s");
        sb.append(", ").append(fileOverrides).append(" file override").append(fileOverrides == 1 ? "" : "s");
        if (!delta.deletedFiles.isEmpty()) {
            sb.append(", ").append(delta.deletedFiles.size()).append(" deletion").append(delta.deletedFiles.size() == 1 ? "" : "s");
        }
        return sb.toString();
    }

    public static void captureFullTo(Path game, Path targetDir) throws IOException {
        cleanDir(targetDir);
        copyIfExists(game.resolve("options.txt"), targetDir.resolve("options.txt"));
        copyTreeIfExists(game.resolve("config"), targetDir.resolve("config"));
        Path shaders = game.resolve("shaderpacks");
        if (Files.isDirectory(shaders)) {
            for (Map.Entry<String, Path> entry : walkRel(shaders).entrySet()) {
                if (entry.getKey().endsWith(".zip.txt")) {
                    copyFile(entry.getValue(), targetDir.resolve("shaderpacks").resolve(entry.getKey()));
                }
            }
        }
    }

    public static void captureDerivedFromLive(Path game, String name, String baseName) throws IOException {
        buildDelta(presetDir(game, name), liveRoots(game), presetDir(game, baseName), baseName);
    }

    public static void applyTo(Path game, String name, Path targetDir) throws IOException {
        applyTo(game, name, targetDir, new MergeStats());
    }

    public static MergeStats applyWithStats(Path game, String name, Path targetDir) throws IOException {
        MergeStats stats = new MergeStats();
        applyTo(game, name, targetDir, stats);
        return stats;
    }

    public static ChangeSet computeSessionChanges(Path game, Path referenceDir) throws IOException {
        ChangeSet cs = new ChangeSet();
        Map<String, String> live = readFlat(game.resolve("options.txt"));
        Map<String, String> ref = referenceDir != null ? readFlat(referenceDir.resolve("options.txt")) : Map.of();

        for (Map.Entry<String, String> entry : live.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value.equals(ref.get(key))) continue;
            if (ListKeys.isListKey(key)) {
                cs.listChanges.put(key, value);
                String refValue = ref.get(key);
                if (refValue != null) {
                    cs.refListValues.put(key, refValue);
                }
            } else {
                cs.scalarChanges.put(key, value);
            }
        }

        Map<String, byte[]> liveFiles = scanLiveFiles(game);
        Map<String, byte[]> refFiles = referenceDir != null ? scanSnapshotFiles(referenceDir) : Map.of();
        for (Map.Entry<String, byte[]> entry : liveFiles.entrySet()) {
            byte[] refBytes = refFiles.get(entry.getKey());
            if (refBytes == null || !Arrays.equals(entry.getValue(), refBytes)) {
                cs.fileChanges.put(entry.getKey(), entry.getValue());
                if (refBytes != null) {
                    cs.refFiles.put(entry.getKey(), refBytes);
                }
            }
        }
        for (String rel : refFiles.keySet()) {
            if (!liveFiles.containsKey(rel)) {
                cs.deletedFiles.add(rel);
            }
        }
        return cs;
    }

    public static void updateBase(Path game, String name, ChangeSet cs) throws IOException {
        Path baseDir = presetDir(game, name);
        Map<String, String> options = readFlat(baseDir.resolve("options.txt"));
        options.putAll(cs.scalarChanges);
        for (Map.Entry<String, String> entry : cs.listChanges.entrySet()) {
            String refValue = cs.refListValues.get(entry.getKey());
            String baseValue = options.get(entry.getKey());
            List<String> liveList = ListKeys.parse(entry.getValue());
            List<String> refList = refValue != null ? ListKeys.parse(refValue) : null;
            List<String> baseList = baseValue != null ? ListKeys.parse(baseValue) : null;
            if (liveList == null || refList == null || baseList == null) {
                options.put(entry.getKey(), entry.getValue());
                continue;
            }
            List<String> added = ListKeys.difference(liveList, refList);
            List<String> removed = ListKeys.difference(refList, liveList);
            if (added.isEmpty() && removed.isEmpty()) {
                options.put(entry.getKey(), entry.getValue());
                continue;
            }
            options.put(entry.getKey(), ListKeys.write(ListKeys.applyDelta(baseList, added, removed)));
        }
        writeFlat(baseDir.resolve("options.txt"), options);

        for (Map.Entry<String, byte[]> entry : cs.fileChanges.entrySet()) {
            Path target = baseDir.resolve(entry.getKey());
            byte[] refBytes = cs.refFiles.get(entry.getKey());
            byte[] currentBytes = Files.exists(target) ? readBytes(target) : null;
            if (refBytes != null && currentBytes != null
                    && isText(refBytes) && isText(currentBytes) && isText(entry.getValue())) {
                if (JsonMerge.canMerge(refBytes, currentBytes, entry.getValue())) {
                    writeBytes(target, JsonMerge.merge(refBytes, currentBytes, entry.getValue()));
                } else {
                    String eol = detectEol(refBytes);
                    Diff3.Result result = Diff3.merge(toLines(refBytes), toLines(currentBytes), toLines(entry.getValue()), DIFFER);
                    writeLines(target, result.merged(), eol);
                }
            } else {
                writeBytes(target, entry.getValue());
            }
        }
        for (String rel : cs.deletedFiles) {
            deleteIfExists(baseDir.resolve(rel));
        }
    }

    public static void updateDerived(Path game, String name, ChangeSet cs) throws IOException {
        updateDerived(game, name, cs, null, null);
    }

    public static void updateDerived(Path game, String name, ChangeSet cs, Path refDir, Path targetDir) throws IOException {
        Path presetDir = presetDir(game, name);
        PresetDelta delta = loadDelta(game, name);
        if (delta == null) {
            delta = new PresetDelta();
        }
        Path baseDir = presetDir(game, delta.base);
        Map<String, String> baseOptions = readFlat(baseDir.resolve("options.txt"));
        Map<String, String> overrides = readFlat(presetDir.resolve("options.txt"));

        for (Map.Entry<String, String> entry : cs.scalarChanges.entrySet()) {
            if (entry.getValue().equals(baseOptions.get(entry.getKey()))) {
                overrides.remove(entry.getKey());
            } else {
                overrides.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<String, String> entry : cs.listChanges.entrySet()) {
            String key = entry.getKey();
            List<String> liveList = ListKeys.parse(entry.getValue());
            List<String> baseList = ListKeys.parse(baseOptions.get(key));
            if (liveList == null || baseList == null) {
                if (entry.getValue() == null || entry.getValue().equals(baseOptions.get(key))) {
                    overrides.remove(key);
                } else {
                    overrides.put(key, entry.getValue());
                }
                delta.listDeltas.remove(key);
                continue;
            }
            if (liveList.equals(baseList)) {
                delta.listDeltas.remove(key);
                continue;
            }
            PresetDelta.ListDelta ld = new PresetDelta.ListDelta();
            if (ListKeys.sameSet(liveList, baseList)) {
                ld.orderedOverride = new ArrayList<>(liveList);
            } else {
                ld.added = ListKeys.difference(liveList, baseList);
                ld.removed = ListKeys.difference(baseList, liveList);
            }
            delta.listDeltas.put(key, ld);
            overrides.remove(key);
        }

        if (overrides.isEmpty()) {
            deleteIfExists(presetDir.resolve("options.txt"));
        } else {
            writeFlat(presetDir.resolve("options.txt"), overrides);
        }

        for (Map.Entry<String, byte[]> entry : cs.fileChanges.entrySet()) {
            String rel = entry.getKey();
            byte[] bytes = entry.getValue();
            if (rel.startsWith("config/")) {
                applyConfigFileChange(presetDir, baseDir, delta, rel.substring("config/".length()), bytes, refDir, targetDir);
            } else if (rel.startsWith("shaderpacks/")) {
                applyShaderFileChange(presetDir, baseDir, delta, rel.substring("shaderpacks/".length()), bytes, refDir, targetDir);
            }
        }

        for (String rel : cs.deletedFiles) {
            applyDeletion(presetDir, baseDir, delta, rel);
        }

        String baseName = delta.base;
        delta.deletedFiles.removeIf(rel -> !Files.exists(presetDir(game, baseName).resolve(rel)));
        saveDelta(presetDir, delta);
    }

    public static void pruneRedundantOverrides(Path game, String name) throws IOException {
        Path presetDir = presetDir(game, name);
        PresetDelta delta = loadDelta(game, name);
        if (delta == null) return;
        Path baseDir = presetDir(game, delta.base);

        Map<String, String> baseOptions = readFlat(baseDir.resolve("options.txt"));
        Map<String, String> overrides = readFlat(presetDir.resolve("options.txt"));
        overrides.entrySet().removeIf(entry -> entry.getValue().equals(baseOptions.get(entry.getKey())));
        if (overrides.isEmpty()) {
            deleteIfExists(presetDir.resolve("options.txt"));
        } else {
            writeFlat(presetDir.resolve("options.txt"), overrides);
        }

        Path overrideConfig = presetDir.resolve("config");
        if (Files.isDirectory(overrideConfig)) {
            for (Map.Entry<String, Path> entry : walkRel(overrideConfig).entrySet()) {
                String rel = entry.getKey();
                Path baseFile = baseDir.resolve("config").resolve(rel);
                if (Files.exists(baseFile) && Arrays.equals(readBytes(entry.getValue()), readBytes(baseFile))) {
                    deleteIfExists(entry.getValue());
                    deleteIfExists(presetDir.resolve(ANCESTOR_DIR).resolve("config").resolve(rel));
                    delta.ancestorHashes.remove("config/" + rel);
                }
            }
        }

        Path overrideShaders = presetDir.resolve("shaderpacks");
        if (Files.isDirectory(overrideShaders)) {
            for (Map.Entry<String, Path> entry : walkRel(overrideShaders).entrySet()) {
                String rel = entry.getKey();
                Path baseFile = baseDir.resolve("shaderpacks").resolve(rel);
                if (!Files.exists(baseFile)) continue;
                if (Files.exists(presetDir.resolve(ANCESTOR_DIR).resolve("shaderpacks").resolve(rel))) {
                    if (Arrays.equals(readBytes(entry.getValue()), readBytes(baseFile))) {
                        deleteIfExists(entry.getValue());
                        deleteIfExists(presetDir.resolve(ANCESTOR_DIR).resolve("shaderpacks").resolve(rel));
                    }
                    continue;
                }
                List<String> overrideLines = Files.readAllLines(entry.getValue(), StandardCharsets.UTF_8);
                List<String> baseLines = Files.readAllLines(baseFile, StandardCharsets.UTF_8);
                if (!FlatKeyFile.wellFormed(overrideLines) || !FlatKeyFile.wellFormed(baseLines)) continue;
                LinkedHashMap<String, String> partial = FlatKeyFile.parse(overrideLines);
                Map<String, String> baseMap = FlatKeyFile.parse(baseLines);
                partial.entrySet().removeIf(e -> e.getValue().equals(baseMap.get(e.getKey())));
                if (partial.isEmpty()) {
                    deleteIfExists(entry.getValue());
                } else {
                    writeFlat(entry.getValue(), partial);
                }
            }
        }

        String baseName = delta.base;
        delta.deletedFiles.removeIf(rel -> !Files.exists(presetDir(game, baseName).resolve(rel)));
        saveDelta(presetDir, delta);
    }

    public static void promoteToBase(Path game, String name, List<String> others) throws IOException {
        Path staging = stagingDir(game);
        cleanDir(staging);
        if (isDerived(game, name)) {
            applyAndSwap(game, name, staging);
        }
        for (String other : others) {
            if (!isDerived(game, other)) continue;
            applyAndSwap(game, other, staging);
        }
        Path baseDir = presetDir(game, name);
        for (String other : others) {
            Path tmp = staging.resolve(other);
            copyTreeIfExists(presetDir(game, other), tmp);
            buildDelta(presetDir(game, other), snapshotRoots(tmp), baseDir, name);
        }
        deleteTree(staging);
    }

    public static void demoteBase(Path game, List<String> others) throws IOException {
        Path staging = stagingDir(game);
        cleanDir(staging);
        for (String other : others) {
            if (!isDerived(game, other)) continue;
            applyAndSwap(game, other, staging);
        }
        deleteTree(staging);
    }

    private static void applyAndSwap(Path game, String name, Path staging) throws IOException {
        Path tmp = staging.resolve(name);
        applyTo(game, name, tmp);
        Path dir = presetDir(game, name);
        deleteTree(dir);
        Files.move(tmp, dir);
    }

    private static void applyTo(Path game, String name, Path targetDir, MergeStats stats) throws IOException {
        Path presetDir = presetDir(game, name);
        cleanDir(targetDir);

        if (!isDerived(game, name)) {
            copyIfExists(presetDir.resolve("options.txt"), targetDir.resolve("options.txt"));
            copyTreeIfExists(presetDir.resolve("config"), targetDir.resolve("config"));
            copyTreeIfExists(presetDir.resolve("shaderpacks"), targetDir.resolve("shaderpacks"));
            return;
        }

        PresetDelta delta = loadDelta(game, name);
        Path baseDir = presetDir(game, delta.base);

        Map<String, String> baseOptions = readFlat(baseDir.resolve("options.txt"));
        Map<String, String> overrides = readFlat(presetDir.resolve("options.txt"));
        LinkedHashMap<String, String> mergedOptions = FlatKeyFile.merge(baseOptions, overrides);
        for (Map.Entry<String, PresetDelta.ListDelta> entry : delta.listDeltas.entrySet()) {
            String key = entry.getKey();
            PresetDelta.ListDelta ld = entry.getValue();
            List<String> baseList = ListKeys.parse(mergedOptions.get(key));
            if (baseList == null) baseList = new ArrayList<>();
            List<String> result = ld.orderedOverride != null
                    ? new ArrayList<>(ld.orderedOverride)
                    : ListKeys.applyDelta(baseList, ld.added, ld.removed);
            mergedOptions.put(key, ListKeys.write(result));
        }
        writeFlat(targetDir.resolve("options.txt"), mergedOptions);

        copyTreeIfExists(baseDir.resolve("config"), targetDir.resolve("config"));
        Path overrideConfig = presetDir.resolve("config");
        if (Files.isDirectory(overrideConfig)) {
            for (Map.Entry<String, Path> entry : walkRel(overrideConfig).entrySet()) {
                String rel = entry.getKey();
                Path overrideFile = entry.getValue();
                Path baseFile = baseDir.resolve("config").resolve(rel);
                Path ancestorFile = presetDir.resolve(ANCESTOR_DIR).resolve("config").resolve(rel);
                mergeTextOrCopy(overrideFile, baseFile, ancestorFile, targetDir.resolve("config").resolve(rel), stats);
            }
        }

        copyTreeIfExists(baseDir.resolve("shaderpacks"), targetDir.resolve("shaderpacks"));
        Path overrideShaders = presetDir.resolve("shaderpacks");
        if (Files.isDirectory(overrideShaders)) {
            for (Map.Entry<String, Path> entry : walkRel(overrideShaders).entrySet()) {
                String rel = entry.getKey();
                Path overrideFile = entry.getValue();
                Path baseFile = baseDir.resolve("shaderpacks").resolve(rel);
                Path ancestorFile = presetDir.resolve(ANCESTOR_DIR).resolve("shaderpacks").resolve(rel);
                Path targetFile = targetDir.resolve("shaderpacks").resolve(rel);

                if (Files.exists(ancestorFile) && Files.exists(baseFile)) {
                    mergeTextOrCopy(overrideFile, baseFile, ancestorFile, targetFile, stats);
                    continue;
                }
                if (Files.exists(baseFile)) {
                    List<String> overrideLines = Files.readAllLines(overrideFile, StandardCharsets.UTF_8);
                    List<String> baseLines = Files.readAllLines(baseFile, StandardCharsets.UTF_8);
                    if (FlatKeyFile.wellFormed(overrideLines) && FlatKeyFile.wellFormed(baseLines)) {
                        LinkedHashMap<String, String> merged = FlatKeyFile.merge(FlatKeyFile.parse(baseLines), FlatKeyFile.parse(overrideLines));
                        writeFlat(targetFile, merged);
                        continue;
                    }
                }
                copyFile(overrideFile, targetFile);
            }
        }

        for (String rel : delta.deletedFiles) {
            deleteIfExists(targetDir.resolve(rel));
        }
    }

    private static void mergeTextOrCopy(Path overrideFile, Path baseFile, Path ancestorFile, Path targetFile, MergeStats stats) throws IOException {
        if (Files.exists(ancestorFile) && Files.exists(baseFile)) {
            byte[] ancestor = readBytes(ancestorFile);
            byte[] base = readBytes(baseFile);
            byte[] override = readBytes(overrideFile);
            if (isText(ancestor) && isText(base) && isText(override)) {
                if (JsonMerge.canMerge(ancestor, base, override)) {
                    writeBytes(targetFile, JsonMerge.merge(ancestor, base, override));
                    stats.textMerged++;
                    return;
                }
                String eol = detectEol(ancestor);
                Diff3.Result result = Diff3.merge(toLines(ancestor), toLines(base), toLines(override), DIFFER);
                writeLines(targetFile, result.merged(), eol);
                stats.conflicts += result.conflicts();
                stats.textMerged++;
                return;
            }
        }
        copyFile(overrideFile, targetFile);
    }

    private static void buildDelta(Path presetDir, SourceRoots src, Path baseDir, String baseName) throws IOException {
        cleanDir(presetDir);
        PresetDelta delta = new PresetDelta();
        delta.base = baseName;

        Map<String, String> baseOptions = readFlat(baseDir.resolve("options.txt"));
        Map<String, String> srcOptions = readFlat(src.options());
        Map<String, String> scalarOverrides = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : srcOptions.entrySet()) {
            String key = entry.getKey();
            if (ListKeys.isListKey(key)) continue;
            if (!entry.getValue().equals(baseOptions.get(key))) {
                scalarOverrides.put(key, entry.getValue());
            }
        }

        for (String key : ListKeys.KEYS) {
            String srcValue = srcOptions.get(key);
            String baseValue = baseOptions.get(key);
            if (srcValue == null ? baseValue == null : srcValue.equals(baseValue)) continue;
            List<String> srcList = ListKeys.parse(srcValue);
            List<String> baseList = ListKeys.parse(baseValue);
            if (srcList == null || baseList == null) {
                if (srcValue != null) scalarOverrides.put(key, srcValue);
                continue;
            }
            PresetDelta.ListDelta ld = new PresetDelta.ListDelta();
            if (ListKeys.sameSet(srcList, baseList)) {
                ld.orderedOverride = new ArrayList<>(srcList);
            } else {
                ld.added = ListKeys.difference(srcList, baseList);
                ld.removed = ListKeys.difference(baseList, srcList);
            }
            if (!ld.isEmpty()) {
                delta.listDeltas.put(key, ld);
            }
        }

        if (!scalarOverrides.isEmpty()) {
            writeFlat(presetDir.resolve("options.txt"), scalarOverrides);
        }

        diffConfigTrees(src.config(), baseDir.resolve("config"), presetDir.resolve("config"), presetDir.resolve(ANCESTOR_DIR).resolve("config"), delta);
        diffShaderTrees(src.shaderpacks(), baseDir.resolve("shaderpacks"), presetDir.resolve("shaderpacks"), presetDir.resolve(ANCESTOR_DIR).resolve("shaderpacks"), delta);

        saveDelta(presetDir, delta);
    }

    private static void diffConfigTrees(Path srcRoot, Path baseRoot, Path outRoot, Path oldRoot, PresetDelta delta) throws IOException {
        Map<String, Path> srcFiles = Files.isDirectory(srcRoot) ? walkRel(srcRoot) : Map.of();
        Map<String, Path> baseFiles = Files.isDirectory(baseRoot) ? walkRel(baseRoot) : Map.of();

        for (Map.Entry<String, Path> entry : srcFiles.entrySet()) {
            String rel = entry.getKey();
            Path srcFile = entry.getValue();
            Path baseFile = baseFiles.get(rel);
            if (baseFile == null) {
                copyFile(srcFile, outRoot.resolve(rel));
                continue;
            }
            byte[] srcBytes = readBytes(srcFile);
            byte[] baseBytes = readBytes(baseFile);
            if (Arrays.equals(srcBytes, baseBytes)) continue;
            writeBytes(outRoot.resolve(rel), srcBytes);
            if (isText(srcBytes) && isText(baseBytes)) {
                writeBytes(oldRoot.resolve(rel), baseBytes);
                delta.ancestorHashes.put("config/" + rel, sha256(baseBytes));
            }
        }
        for (String rel : baseFiles.keySet()) {
            if (!srcFiles.containsKey(rel)) {
                delta.deletedFiles.add("config/" + rel);
            }
        }
    }

    private static void diffShaderTrees(Path srcRoot, Path baseRoot, Path outRoot, Path oldRoot, PresetDelta delta) throws IOException {
        Map<String, Path> srcFiles = Files.isDirectory(srcRoot) ? shaderTxts(srcRoot) : Map.of();
        Map<String, Path> baseFiles = Files.isDirectory(baseRoot) ? shaderTxts(baseRoot) : Map.of();

        for (Map.Entry<String, Path> entry : srcFiles.entrySet()) {
            String rel = entry.getKey();
            Path srcFile = entry.getValue();
            Path baseFile = baseFiles.get(rel);
            if (baseFile == null) {
                copyFile(srcFile, outRoot.resolve(rel));
                continue;
            }
            byte[] srcBytes = readBytes(srcFile);
            byte[] baseBytes = readBytes(baseFile);
            if (Arrays.equals(srcBytes, baseBytes)) continue;

            List<String> srcLines = Files.readAllLines(srcFile, StandardCharsets.UTF_8);
            List<String> baseLines = Files.readAllLines(baseFile, StandardCharsets.UTF_8);
            if (FlatKeyFile.wellFormed(srcLines) && FlatKeyFile.wellFormed(baseLines)) {
                LinkedHashMap<String, String> partial = new LinkedHashMap<>();
                Map<String, String> srcMap = FlatKeyFile.parse(srcLines);
                Map<String, String> baseMap = FlatKeyFile.parse(baseLines);
                for (Map.Entry<String, String> e : srcMap.entrySet()) {
                    if (!e.getValue().equals(baseMap.get(e.getKey()))) {
                        partial.put(e.getKey(), e.getValue());
                    }
                }
                if (!partial.isEmpty()) {
                    writeFlat(outRoot.resolve(rel), partial);
                }
                continue;
            }
            writeBytes(outRoot.resolve(rel), srcBytes);
            if (isText(srcBytes) && isText(baseBytes)) {
                writeBytes(oldRoot.resolve(rel), baseBytes);
                delta.ancestorHashes.put("shaderpacks/" + rel, sha256(baseBytes));
            }
        }
        for (String rel : baseFiles.keySet()) {
            if (!srcFiles.containsKey(rel)) {
                delta.deletedFiles.add("shaderpacks/" + rel);
            }
        }
    }

    private static void applyConfigFileChange(Path presetDir, Path baseDir, PresetDelta delta, String rel, byte[] bytes, Path refDir, Path targetDir) throws IOException {
        Path baseFile = baseDir.resolve("config").resolve(rel);
        Path overrideFile = presetDir.resolve("config").resolve(rel);
        Path ancestorFile = presetDir.resolve(ANCESTOR_DIR).resolve("config").resolve(rel);

        Path refFile = refDir != null ? refDir.resolve("config").resolve(rel) : null;
        Path targetFile = targetDir != null ? targetDir.resolve("config").resolve(rel) : null;
        byte[] result = mergeSessionFile(refFile, targetFile, bytes);

        if (Files.exists(baseFile) && Arrays.equals(result, readBytes(baseFile))) {
            deleteIfExists(overrideFile);
            deleteIfExists(ancestorFile);
            delta.ancestorHashes.remove("config/" + rel);
            return;
        }
        writeBytes(overrideFile, result);
        if (Files.exists(baseFile)) {
            byte[] baseBytes = readBytes(baseFile);
            if (isText(result) && isText(baseBytes)) {
                writeBytes(ancestorFile, baseBytes);
                delta.ancestorHashes.put("config/" + rel, sha256(baseBytes));
                return;
            }
        }
        deleteIfExists(ancestorFile);
        delta.ancestorHashes.remove("config/" + rel);
    }

    private static void applyShaderFileChange(Path presetDir, Path baseDir, PresetDelta delta, String rel, byte[] bytes, Path refDir, Path targetDir) throws IOException {
        Path baseFile = baseDir.resolve("shaderpacks").resolve(rel);
        Path overrideFile = presetDir.resolve("shaderpacks").resolve(rel);
        Path ancestorFile = presetDir.resolve(ANCESTOR_DIR).resolve("shaderpacks").resolve(rel);

        if (Files.exists(baseFile) && Arrays.equals(bytes, readBytes(baseFile))) {
            deleteIfExists(overrideFile);
            deleteIfExists(ancestorFile);
            delta.ancestorHashes.remove("shaderpacks/" + rel);
            return;
        }

        Path refFile = refDir != null ? refDir.resolve("shaderpacks").resolve(rel) : null;
        Path targetFile = targetDir != null ? targetDir.resolve("shaderpacks").resolve(rel) : null;

        if (Files.exists(baseFile)) {
            byte[] baseBytes = readBytes(baseFile);
            if (isText(bytes) && isText(baseBytes)) {
                List<String> liveLines = toLines(bytes);
                List<String> baseLines = toLines(baseBytes);
                if (FlatKeyFile.wellFormed(liveLines) && FlatKeyFile.wellFormed(baseLines)) {
                    Map<String, String> liveMap = FlatKeyFile.parse(liveLines);
                    Map<String, String> baseMap = FlatKeyFile.parse(baseLines);
                    Map<String, String> refMap = parseFlatOrNull(refFile);
                    Map<String, String> targetMap = targetFile != null ? parseFlatOrNull(targetFile) : null;
                    if (targetMap == null && refMap != null) {
                        targetMap = refMap;
                    }
                    if (refMap != null && targetMap != null) {
                        for (Map.Entry<String, String> e : targetMap.entrySet()) {
                            String liveValue = liveMap.get(e.getKey());
                            if (liveValue == null) {
                                if (!refMap.containsKey(e.getKey())) {
                                    liveMap.put(e.getKey(), e.getValue());
                                }
                            } else if (liveValue.equals(refMap.get(e.getKey()))) {
                                liveMap.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                    LinkedHashMap<String, String> partial = new LinkedHashMap<>();
                    for (Map.Entry<String, String> e : liveMap.entrySet()) {
                        if (!e.getValue().equals(baseMap.get(e.getKey()))) {
                            partial.put(e.getKey(), e.getValue());
                        }
                    }
                    deleteIfExists(ancestorFile);
                    delta.ancestorHashes.remove("shaderpacks/" + rel);
                    if (partial.isEmpty()) {
                        deleteIfExists(overrideFile);
                    } else {
                        writeFlat(overrideFile, partial);
                    }
                    return;
                }
                byte[] result = mergeSessionFile(refFile, targetFile, bytes);
                writeBytes(overrideFile, result);
                writeBytes(ancestorFile, baseBytes);
                delta.ancestorHashes.put("shaderpacks/" + rel, sha256(baseBytes));
                return;
            }
        }
        writeBytes(overrideFile, bytes);
        deleteIfExists(ancestorFile);
        delta.ancestorHashes.remove("shaderpacks/" + rel);
    }

    private static Map<String, String> parseFlatOrNull(Path file) throws IOException {
        if (file == null || !Files.exists(file)) return null;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (!FlatKeyFile.wellFormed(lines)) return null;
        return FlatKeyFile.parse(lines);
    }

    private static byte[] mergeSessionFile(Path refFile, Path targetFile, byte[] theirs) throws IOException {
        byte[] ancestor = refFile != null && Files.exists(refFile) ? readBytes(refFile) : null;
        byte[] ours = targetFile != null && Files.exists(targetFile) ? readBytes(targetFile) : ancestor;
        if (ancestor == null || ours == null || !isText(ancestor) || !isText(ours) || !isText(theirs)) {
            return theirs;
        }
        if (JsonMerge.canMerge(ancestor, ours, theirs)) {
            return JsonMerge.merge(ancestor, ours, theirs);
        }
        String eol = detectEol(ancestor);
        Diff3.Result merged = Diff3.merge(toLines(ancestor), toLines(ours), toLines(theirs), DIFFER);
        StringBuilder sb = new StringBuilder();
        for (String line : merged.merged()) {
            sb.append(line).append(eol);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void applyDeletion(Path presetDir, Path baseDir, PresetDelta delta, String rel) throws IOException {
        boolean baseHas = Files.exists(baseDir.resolve(rel));
        if (rel.startsWith("config/")) {
            String sub = rel.substring("config/".length());
            deleteIfExists(presetDir.resolve("config").resolve(sub));
            deleteIfExists(presetDir.resolve(ANCESTOR_DIR).resolve("config").resolve(sub));
        } else if (rel.startsWith("shaderpacks/")) {
            String sub = rel.substring("shaderpacks/".length());
            deleteIfExists(presetDir.resolve("shaderpacks").resolve(sub));
            deleteIfExists(presetDir.resolve(ANCESTOR_DIR).resolve("shaderpacks").resolve(sub));
        }
        delta.ancestorHashes.remove(rel);
        if (baseHas) {
            if (!delta.deletedFiles.contains(rel)) {
                delta.deletedFiles.add(rel);
            }
        } else {
            delta.deletedFiles.remove(rel);
        }
    }

    private static SourceRoots liveRoots(Path game) {
        return new SourceRoots(game.resolve("options.txt"), game.resolve("config"), game.resolve("shaderpacks"));
    }

    private static SourceRoots snapshotRoots(Path dir) {
        return new SourceRoots(dir.resolve("options.txt"), dir.resolve("config"), dir.resolve("shaderpacks"));
    }

    private static Map<String, byte[]> scanLiveFiles(Path game) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.isDirectory(game.resolve("config"))) {
            for (Map.Entry<String, Path> entry : walkRel(game.resolve("config")).entrySet()) {
                files.put("config/" + entry.getKey(), readBytes(entry.getValue()));
            }
        }
        if (Files.isDirectory(game.resolve("shaderpacks"))) {
            for (Map.Entry<String, Path> entry : shaderTxts(game.resolve("shaderpacks")).entrySet()) {
                files.put("shaderpacks/" + entry.getKey(), readBytes(entry.getValue()));
            }
        }
        return files;
    }

    private static Map<String, byte[]> scanSnapshotFiles(Path dir) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.isDirectory(dir.resolve("config"))) {
            for (Map.Entry<String, Path> entry : walkRel(dir.resolve("config")).entrySet()) {
                files.put("config/" + entry.getKey(), readBytes(entry.getValue()));
            }
        }
        if (Files.isDirectory(dir.resolve("shaderpacks"))) {
            for (Map.Entry<String, Path> entry : walkRel(dir.resolve("shaderpacks")).entrySet()) {
                files.put("shaderpacks/" + entry.getKey(), readBytes(entry.getValue()));
            }
        }
        return files;
    }

    private static void saveDelta(Path presetDir, PresetDelta delta) throws IOException {
        Files.createDirectories(presetDir);
        Files.writeString(presetDir.resolve(DELTA_FILE), GSON.toJson(delta));
    }

    private static Map<String, Path> shaderTxts(Path root) throws IOException {
        Map<String, Path> out = new TreeMap<>();
        for (Map.Entry<String, Path> entry : walkRel(root).entrySet()) {
            if (entry.getKey().endsWith(".zip.txt")) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static TreeMap<String, Path> walkRel(Path root) throws IOException {
        TreeMap<String, Path> out = new TreeMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> out.put(toRel(root, file), file));
        }
        return out;
    }

    private static String toRel(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static LinkedHashMap<String, String> readFlat(Path file) throws IOException {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        return FlatKeyFile.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    private static void writeFlat(Path file, Map<String, String> map) throws IOException {
        if (map.isEmpty()) {
            deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", FlatKeyFile.serialize(map)) + "\n", StandardCharsets.UTF_8);
    }

    private static boolean isText(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static String detectEol(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).contains("\r\n") ? "\r\n" : "\n";
    }

    private static List<String> toLines(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        lines.replaceAll(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        return lines;
    }

    private static void writeLines(Path file, List<String> lines, String eol) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join(eol, lines), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readBytes(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    private static void writeBytes(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static void copyIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            copyFile(source, target);
        }
    }

    private static void copyFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyTreeIfExists(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return;
        for (Map.Entry<String, Path> entry : walkRel(source).entrySet()) {
            copyFile(entry.getValue(), target.resolve(entry.getKey()));
        }
    }

    private static void deleteIfExists(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    private static void cleanDir(Path dir) throws IOException {
        deleteTree(dir);
        Files.createDirectories(dir);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteTreeQuietly(Path dir) {
        try {
            deleteTree(dir);
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up directory: {}", dir, e);
        }
    }
}
