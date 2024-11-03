package com.badbones69.crazycrates.api;

import com.badbones69.crazycrates.CrazyCrates;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileManager {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    private final Map<Files, File> files = new ConcurrentHashMap<>();
    private final List<String> homeFolders = new ArrayList<>();
    private final List<CustomFile> customFiles = new ArrayList<>();
    private final Map<String, String> jarHomeFolders = new ConcurrentHashMap<>();
    private final Map<String, String> autoGenerateFiles = new ConcurrentHashMap<>();
    private final Map<Files, FileConfiguration> configurations = new ConcurrentHashMap<>();
    private final Logger logger = this.plugin.getLogger();
    private final boolean isLogging = this.plugin.isLogging();

    /**
     * Sets up the plugin and loads all necessary files.
     */
    public void setup() {
        final File dataFolder = this.plugin.getDataFolder();

        if (!dataFolder.exists()) dataFolder.mkdirs();

        this.files.clear();
        this.customFiles.clear();
        this.configurations.clear();

        // Loads all the normal static files.
        for (final Files file : Files.values()) {
            final File newFile = new File(dataFolder, file.getFileLocation());

            this.plugin.debug(() -> "Loading the " + file.getFileName(), Level.INFO);

            if (!newFile.exists()) {
                try {
                    final File serverFile = new File(dataFolder, "/" + file.getFileLocation());
                    final InputStream jarFile = getClass().getResourceAsStream("/" + file.getFileJar());

                    copyFile(jarFile, serverFile);
                } catch (final Exception exception) {
                    this.logger.log(Level.SEVERE, "Failed to load file: " + file.getFileName(), exception);

                    continue;
                }
            }

            this.files.put(file, newFile);

            if (file.getFileName().endsWith(".yml")) {
                this.configurations.put(file, YamlConfiguration.loadConfiguration(newFile));
            }

            if (this.isLogging) this.logger.info("Successfully loaded " + file.getFileName());
        }

        if (this.homeFolders.isEmpty()) return;

        // Starts to load all the custom files.
        if (this.isLogging) this.logger.info("Loading custom files.");

        for (String homeFolder : this.homeFolders) {
            final File homeFile = new File(dataFolder, "/" + homeFolder);

            if (homeFile.exists()) {
                final File[] filesList = homeFile.listFiles();

                if (filesList != null) {
                    for (final File directory : filesList) {
                        if (directory.isDirectory()) {
                            final String[] folder = directory.list();

                            if (folder != null) {
                                for (final String name : folder) {
                                    if (!name.endsWith(".yml")) continue;

                                    final CustomFile file = new CustomFile(name, homeFolder + "/", directory.getName());

                                    if (file.exists()) {
                                        this.customFiles.add(file);

                                        if (this.isLogging)
                                            this.logger.info("Loaded new custom file: " + homeFolder + "/" + directory.getName() + "/" + name + ".");
                                    }
                                }
                            }
                        } else {
                            final String name = directory.getName();

                            if (!name.endsWith(".yml")) continue;

                            final CustomFile file = new CustomFile(name, homeFolder);

                            if (file.exists()) {
                                this.customFiles.add(file);

                                if (this.isLogging)
                                    this.logger.info("Loaded new custom file: " + homeFolder + "/" + name + ".");
                            }
                        }
                    }
                }
            } else {
                homeFile.mkdir();

                if (this.isLogging) this.logger.info("The folder " + homeFolder + "/ was not found so it was created.");

                for (final String fileName : this.autoGenerateFiles.keySet()) {
                    if (this.autoGenerateFiles.get(fileName).equalsIgnoreCase(homeFolder)) {
                        homeFolder = this.autoGenerateFiles.get(fileName);

                        try (final InputStream jarFile = getClass().getResourceAsStream((this.jarHomeFolders.getOrDefault(fileName, homeFolder)) + "/" + fileName)) {
                            final File serverFile = new File(dataFolder, homeFolder + "/" + fileName);

                            copyFile(jarFile, serverFile);

                            if (fileName.toLowerCase().endsWith(".yml"))
                                this.customFiles.add(new CustomFile(fileName, homeFolder));

                            if (this.isLogging)
                                this.logger.info("Created new default file: " + homeFolder + "/" + fileName + ".");
                        } catch (final Exception exception) {
                            this.logger.log(Level.SEVERE, "Failed to create new default file: " + homeFolder + "/" + fileName + "!", exception);
                        }
                    }
                }
            }
        }

        if (this.isLogging) this.logger.info("Finished loading custom files.");
    }

    /**
     * Register a folder that has custom files in it. Make sure to have a "/" in front of the folder name.
     *
     * @param homeFolder the folder that has custom files in it.
     */
    public FileManager registerCustomFilesFolder(final String homeFolder) {
        this.homeFolders.add(homeFolder);

        return this;
    }

    /**
     * Register a file that needs to be generated when it's home folder doesn't exist. Make sure to have a "/" in front of the home folder's name.
     *
     * @param fileName      the name of the file you want to auto-generate when the folder doesn't exist.
     * @param homeFolder    the folder that has custom files in it.
     * @param jarHomeFolder the folder that the file is found in the jar.
     */
    public FileManager registerDefaultGenerateFiles(final String fileName, final String homeFolder, final String jarHomeFolder) {
        this.autoGenerateFiles.put(fileName, homeFolder);
        this.jarHomeFolders.put(fileName, jarHomeFolder);

        return this;
    }

    /**
     * Gets the file from the system.
     *
     * @return the file from the system.
     */
    public FileConfiguration getFile(final Files file) {
        return this.configurations.get(file);
    }

    /**
     * Get a custom file from the loaded custom files instead of a hardcoded one.
     * This allows you to get custom files like Per player data files.
     *
     * @param name name of the crate you want. (Without the .yml)
     * @return the custom file you wanted otherwise if not found will return null.
     */
    public CustomFile getFile(final String name) {
        for (final CustomFile file : this.customFiles) {
            if (file.getName().equalsIgnoreCase(name)) return file;
        }

        return null;
    }

    /**
     * Saves the file from the loaded state to the file system.
     */
    public void saveFile(final Files file) {
        try {
            this.configurations.get(file).save(this.files.get(file));
        } catch (final IOException exception) {
            this.logger.log(Level.SEVERE, "Could not save " + file.getFileName() + "!", exception);
        }
    }


    /**
     * Overrides the loaded state file and loads the file systems file.
     */
    public void reloadFile(final Files file) {
        if (file.getFileName().endsWith(".yml"))
            this.configurations.put(file, YamlConfiguration.loadConfiguration(this.files.get(file)));
    }

    /**
     * Overrides the loaded state file and loads the file systems file.
     */
    public void reloadFile(final String name) {
        final CustomFile file = getFile(name);

        if (file != null) {
            try {
                file.file = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "/" + file.getHomeFolder() + "/" + file.getFileName()));

                if (this.plugin.isLogging())
                    this.plugin.getLogger().info("Successfully reloaded the " + file.getFileName() + ".");
            } catch (final Exception exception) {
                this.logger.log(Level.SEVERE, "Could not reload the " + file.getFileName() + "!", exception);
            }
        } else {
            if (this.plugin.isLogging())
                this.plugin.getLogger().warning("The file " + name + ".yml could not be found!");
        }
    }

    /**
     * @return A list of crate names.
     */
    public List<String> getAllCratesNames() {
        final List<String> fileList = new ArrayList<>();

        final File crateDirectory = new File(this.plugin.getDataFolder(), "/crates");

        final String[] file = crateDirectory.list();

        if (file != null) {
            final File[] filesList = crateDirectory.listFiles();

            if (filesList != null) {
                for (final File directory : filesList) {
                    if (directory.isDirectory()) {
                        final String[] folder = directory.list();

                        if (folder != null) {
                            for (final String name : folder) {
                                if (!name.endsWith(".yml")) continue;

                                fileList.add(name.replaceAll(".yml", ""));
                            }
                        }
                    }
                }
            }

            for (final String name : file) {
                if (!name.endsWith(".yml")) continue;

                fileList.add(name.replaceAll(".yml", ""));
            }
        }

        return Collections.unmodifiableList(fileList);
    }

    /**
     * Was found here: <a href="https://bukkit.org/threads/extracting-file-from-jar.16962">...</a>
     */
    private void copyFile(final InputStream in, final File out) throws Exception {
        try (final InputStream fis = in; final FileOutputStream fos = new FileOutputStream(out)) {
            final byte[] buf = new byte[1024];
            int i;

            while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        }
    }

    public enum Files {

        // ENUM_NAME("fileName.yml", "fileLocation.yml"),
        // ENUM_NAME("fileName.yml", "newFileLocation.yml", "oldFileLocation.yml"),
        LOGS("events.log", "events.log"),
        LOCATIONS("locations.yml", "locations.yml"),
        DATA("data.yml", "data.yml");

        private final String fileName;
        private final String fileJar;
        private final String fileLocation;

        @NotNull
        private final CrazyCrates plugin = CrazyCrates.get();

        @NotNull
        private final FileManager fileManager = this.plugin.getFileManager();

        /**
         * The files that the server will try and load.
         *
         * @param fileName     the file name that will be in the plugin's folder.
         * @param fileLocation the location the file in the plugin's folder.
         */
        Files(final String fileName, final String fileLocation) {
            this(fileName, fileLocation, fileLocation);
        }

        /**
         * The files that the server will try and load.
         *
         * @param fileName     the file name that will be in the plugin's folder.
         * @param fileLocation the location of the file will be in the plugin's folder.
         * @param fileJar      the location of the file in the jar.
         */
        Files(final String fileName, final String fileLocation, final String fileJar) {
            this.fileName = fileName;
            this.fileLocation = fileLocation;
            this.fileJar = fileJar;
        }

        /**
         * Get the name of the file.
         *
         * @return the name of the file.
         */
        public String getFileName() {
            return this.fileName;
        }

        /**
         * The location the jar it is at.
         *
         * @return the location in the jar the file is in.
         */
        public String getFileLocation() {
            return this.fileLocation;
        }

        /**
         * Get the location of the file in the jar.
         *
         * @return the location of the file in the jar.
         */
        public String getFileJar() {
            return this.fileJar;
        }

        /**
         * Gets the file from the system.
         *
         * @return the file from the system.
         */
        public FileConfiguration getFile() {
            return this.fileManager.getFile(this);
        }

        /**
         * Saves the file from the loaded state to the file system.
         */
        public void saveFile() {
            Bukkit.getAsyncScheduler().runNow(this.plugin, task -> this.fileManager.saveFile(this));
        }

        /**
         * Overrides the loaded state file and loads the file systems file.
         */
        public void reloadFile() {
            if (getFileName().endsWith(".yml")) {
                this.fileManager.reloadFile(this);
            }
        }
    }

    public static class CustomFile {

        private final String name;
        private final String fileName;
        private final String homeFolder;
        @NotNull
        private final CrazyCrates plugin = CrazyCrates.get();
        private FileConfiguration file;

        /**
         * A custom file that is being made.
         *
         * @param name       name of the file.
         * @param homeFolder the home folder of the file.
         */
        public CustomFile(final String name, final String homeFolder) {
            this.name = name.replace(".yml", "");
            this.fileName = name;
            this.homeFolder = homeFolder;

            final File root = new File(this.plugin.getDataFolder(), "/" + homeFolder);

            if (!root.exists()) {
                root.mkdirs();

                if (this.plugin.isLogging())
                    this.plugin.getLogger().info("The folder " + homeFolder + "/ was not found so it was created.");

                this.file = null;

                return;
            }

            final File newFile = new File(root, "/" + name);

            if (newFile.exists()) {
                this.file = YamlConfiguration.loadConfiguration(newFile);

                return;
            }

            this.file = null;
        }

        /**
         * A custom file that is being made.
         *
         * @param name       name of the file.
         * @param homeFolder the home folder of the file.
         */
        public CustomFile(final String name, final String homeFolder, final String subFolder) {
            this.name = name.replace(".yml", "");
            this.fileName = name;
            this.homeFolder = homeFolder + "/" + subFolder;

            final File root = new File(this.plugin.getDataFolder(), "/" + this.homeFolder);

            final File newFile = new File(root, "/" + name);

            if (newFile.exists()) {
                this.file = YamlConfiguration.loadConfiguration(newFile);

                return;
            }

            this.file = null;
        }

        /**
         * Get the name of the file without the .yml part.
         *
         * @return the name of the file without the .yml.
         */
        public String getName() {
            return this.name;
        }

        /**
         * Get the full name of the file.
         *
         * @return full name of the file.
         */
        public String getFileName() {
            return this.fileName;
        }

        /**
         * Get the name of the home folder of the file.
         *
         * @return the name of the home folder the files are in.
         */
        public String getHomeFolder() {
            return this.homeFolder;
        }

        /**
         * Get the ConfigurationFile.
         *
         * @return the ConfigurationFile of this file.
         */
        public FileConfiguration getFile() {
            return this.file;
        }

        /**
         * Check if the file actually exists in the file system.
         *
         * @return true if it does and false if it doesn't.
         */
        public boolean exists() {
            return this.file != null;
        }

        /**
         * Save the custom file.
         */
        private void saveFile() {
            if (this.file == null) {
                if (this.plugin.isLogging())
                    this.plugin.getLogger().warning("There was a null custom file that could not be found!");

                return;
            }

            try {
                this.file.save(new File(this.plugin.getDataFolder(), this.homeFolder + "/" + this.fileName));

                if (this.plugin.isLogging())
                    this.plugin.getLogger().info("Successfully saved the " + this.fileName + ".");
            } catch (final IOException exception) {
                this.plugin.getLogger().log(Level.SEVERE, "Could not save " + this.fileName + "!", exception);
            }
        }

        /**
         * Overrides the loaded state file and loads the filesystems file.
         */
        public void reloadFile() {
            if (this.file == null) {
                if (this.plugin.isLogging())
                    this.plugin.getLogger().warning("There was a null custom file that could not be found!");

                return;
            }

            try {
                this.file = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "/" + this.homeFolder + "/" + this.fileName));

                if (this.plugin.isLogging())
                    this.plugin.getLogger().info("Successfully reloaded the " + this.fileName + ".");
            } catch (final Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "Could not reload the " + this.fileName + "!", exception);
            }
        }
    }
}