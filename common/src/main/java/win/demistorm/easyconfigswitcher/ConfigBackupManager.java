package win.demistorm.easyconfigswitcher;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

public final class ConfigBackupManager {

    private static final Logger LOGGER = EasyConfigSwitcher.LOGGER;
    private static final Path ECS_DIR = Path.of("ecs-presets");
    private static final Path PRESETS_DIR = ECS_DIR.resolve("presets");

    private static final Path OPTIONS_FILE = Path.of("options.txt");
    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path SHADERPACKS_DIR = Path.of("shaderpacks");

    private ConfigBackupManager() {
    }

    public static boolean backupPreset(String presetName) {
        Path presetDir = PRESETS_DIR.resolve(presetName);
        try {
            Files.createDirectories(presetDir);
            LOGGER.info("Creating preset backup: {}", presetName);

            if (Files.exists(OPTIONS_FILE)) {
                copyFile(OPTIONS_FILE, presetDir.resolve("options.txt"));
            }

            if (Files.exists(CONFIG_DIR)) {
                Path presetConfigDir = presetDir.resolve("config");
                copyDirectory(CONFIG_DIR, presetConfigDir);
            }

            if (Files.exists(SHADERPACKS_DIR)) {
                Path presetShaderDir = presetDir.resolve("shaderpacks");
                backupShaderSettings(presetShaderDir);
            }

            LOGGER.info("Preset backup created successfully: {}", presetName);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to create preset backup: {}", presetName, e);
            return false;
        }
    }

    public static boolean restorePreset(String presetName) {
        Path presetDir = PRESETS_DIR.resolve(presetName);
        if (!Files.exists(presetDir)) {
            LOGGER.error("Cannot restore preset '{}' - preset directory does not exist", presetName);
            return false;
        }

        try {
            LOGGER.info("Restoring preset: {}", presetName);

            Path presetOptionsFile = presetDir.resolve("options.txt");
            if (Files.exists(presetOptionsFile)) {
                copyFile(presetOptionsFile, OPTIONS_FILE);
            }

            Path presetConfigDir = presetDir.resolve("config");
            if (Files.exists(presetConfigDir)) {
                if (!Files.exists(CONFIG_DIR)) {
                    Files.createDirectories(CONFIG_DIR);
                }
                deleteDirectoryContents();
                copyDirectory(presetConfigDir, CONFIG_DIR);
            }

            Path presetShaderDir = presetDir.resolve("shaderpacks");
            if (Files.exists(presetShaderDir)) {
                restoreShaderSettings(presetShaderDir);
            }

            LOGGER.info("Preset restored successfully: {}", presetName);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to restore preset: {}", presetName, e);
            return false;
        }
    }

    public static void deletePreset(String presetName) {
        Path presetDir = PRESETS_DIR.resolve(presetName);
        try {
            if (Files.exists(presetDir)) {
                deleteDirectory(presetDir);
                LOGGER.info("Preset deleted: {}", presetName);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to delete preset: {}", presetName, e);
        }
    }

    public static boolean renamePreset(String oldName, String newName) {
        Path oldDir = PRESETS_DIR.resolve(oldName);
        Path newDir = PRESETS_DIR.resolve(newName);

        if (!Files.exists(oldDir)) {
            LOGGER.error("Cannot rename preset '{}' - preset does not exist", oldName);
            return false;
        }

        try {
            Files.move(oldDir, newDir, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Preset renamed: {} -> {}", oldName, newName);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to rename preset: {} -> {}", oldName, newName, e);
            return false;
        }
    }

    public static boolean presetExists(String presetName) {
        return Files.exists(PRESETS_DIR.resolve(presetName));
    }

    private static void copyFile(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectoryContents() throws IOException {
        if (!Files.exists(ConfigBackupManager.CONFIG_DIR)) {
            return;
        }
        Files.walkFileTree(ConfigBackupManager.CONFIG_DIR, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(ConfigBackupManager.CONFIG_DIR)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void backupShaderSettings(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.list(ConfigBackupManager.SHADERPACKS_DIR)) {
            stream.filter(path -> path.toString().endsWith(".zip.txt"))
                    .forEach(path -> {
                        try {
                            Files.copy(path, targetDir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            LOGGER.warn("Failed to backup shader settings file: {}", path, e);
                        }
                    });
        }
    }

    private static void restoreShaderSettings(Path sourceDir) throws IOException {
        Files.createDirectories(ConfigBackupManager.SHADERPACKS_DIR);
        try (Stream<Path> stream = Files.list(sourceDir)) {
            stream.filter(path -> path.toString().endsWith(".zip.txt"))
                    .forEach(path -> {
                        try {
                            Files.copy(path, ConfigBackupManager.SHADERPACKS_DIR.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            LOGGER.warn("Failed to restore shader settings file: {}", path, e);
                        }
                    });
        }
    }

}
