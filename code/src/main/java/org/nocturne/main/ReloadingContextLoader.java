/*
 * Copyright 2009 Mike Mirzayanov
 */
package org.nocturne.main;

import org.nocturne.exception.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Mike Mirzayanov
 */
class ReloadingContextLoader {
    public static final String CONFIGURATION_FILE = "/nocturne.properties";
    private static final Properties properties = new Properties();

    static void run() {
        setupDebug();
        setupSkipRegex();

        if (ReloadingContext.getInstance().isDebug()) {
            setupReloadingClassPaths();
            setupClassReloadingPackages();
            setupClassReloadingExceptions();
        }
    }

    private static void setupClassReloadingExceptions() {
        List<String> exceptions = new ArrayList<String>();
        exceptions.add(ReloadingContext.class.getName());
        if (properties.containsKey("nocturne.class-reloading-exceptions")) {
            String exceptionsAsString = properties.getProperty("nocturne.class-reloading-exceptions");
            if (exceptionsAsString != null) {
                String[] candidats = exceptionsAsString.split("\\s*;\\s*");
                for (String item : candidats) {
                    if (!item.isEmpty()) {
                        exceptions.add(item);
                    }
                }
            }
        }
        ReloadingContext.getInstance().setClassReloadingExceptions(exceptions);
    }

    private static void setupSkipRegex() {
        if (properties.containsKey("nocturne.skip-regex")) {
            String regex = properties.getProperty("nocturne.skip-regex");
            if (regex != null && !regex.isEmpty()) {
                try {
                    ReloadingContext.getInstance().setSkipRegex(Pattern.compile(regex));
                } catch (PatternSyntaxException e) {
                    throw new ConfigurationException("Parameter nocturne.skip-regex contains invalid pattern.");
                }
            }
        }
    }

    private static void setupClassReloadingPackages() {
        List<String> packages = new ArrayList<String>();
        packages.add("org.nocturne");
        if (properties.containsKey("nocturne.class-reloading-packages")) {
            String packagesAsString = properties.getProperty("nocturne.class-reloading-packages");
            if (packagesAsString != null) {
                String[] candidats = packagesAsString.split("\\s*;\\s*");
                for (String item : candidats) {
                    if (!item.isEmpty()) {
                        packages.add(item);
                    }
                }
            }
        }
        ReloadingContext.getInstance().setClassReloadingPackages(packages);
    }

    private static void setupReloadingClassPaths() {
        List<File> reloadingClassPaths = new ArrayList<File>();
        if (properties.containsKey("nocturne.reloading-class-paths")) {
            String reloadingClassPathsAsString = properties.getProperty("nocturne.reloading-class-paths");
            if (reloadingClassPathsAsString != null) {
                String[] dirs = reloadingClassPathsAsString.split("\\s*;\\s*");
                for (String dir : dirs) {
                    if (!dir.isEmpty()) {
                        File file = new File(dir);
                        if (!file.isDirectory() && ReloadingContext.getInstance().isDebug()) {
                            throw new ConfigurationException("Each item in nocturne.reloading-class-paths should be a directory.");
                        }
                        reloadingClassPaths.add(file);
                    }
                }
            }
        }
        ReloadingContext.getInstance().setReloadingClassPaths(reloadingClassPaths);
    }

    private static void setupDebug() {
        boolean debug = false;

        if (properties.containsKey("nocturne.debug")) {
            try {
                debug = Boolean.valueOf(properties.getProperty("nocturne.debug"));
            } catch (NullPointerException e) {
                throw new ConfigurationException("Can't cast nocturne.debug to boolean.");
            }
        }

        ReloadingContext.getInstance().setDebug(debug);
    }

    static {
        InputStream inputStream = ApplicationContextLoader.class.getResourceAsStream(CONFIGURATION_FILE);

        try {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new ConfigurationException("Can't load resource file " + CONFIGURATION_FILE + '.', e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // No operations.
                }
            }
        }
    }
}