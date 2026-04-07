package com.isw2project.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the application configuration from a YAML file on the classpath.
 * Uses the Strategy pattern implicitly — swap this class to support other formats.
 */
public class ConfigLoader {

    private ConfigLoader() {}

    /**
     * Loads config from a file on the classpath.
     *
     * @param filename the YAML config filename (e.g. "config.yaml")
     * @return populated AppConfig
     */
    public static AppConfig load(String filename) {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(AppConfig.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);

        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                throw new IllegalStateException("Config file not found on classpath: " + filename);
            }
            return yaml.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config file: " + filename, e);
        }
    }
}