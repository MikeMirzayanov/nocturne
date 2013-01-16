/*
 * Copyright 2009 Mike Mirzayanov
 */
package org.nocturne.main;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.nocturne.exception.ConfigurationException;
import org.nocturne.exception.ModuleInitializationException;
import org.nocturne.exception.NocturneException;
import org.nocturne.module.Module;
import org.nocturne.reset.ResetStrategy;
import org.nocturne.reset.annotation.Persist;
import org.nocturne.reset.annotation.Reset;
import org.nocturne.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Mike Mirzayanov
 */
class ApplicationContextLoader {
    public static final String CONFIGURATION_FILE = "/nocturne.properties";
    private static final Properties properties = new Properties();
    private static final Pattern ITEMS_SPLIT_PATTERN = Pattern.compile("\\s*;\\s*");
    private static final Pattern LANGUAGES_SPLIT_PATTERN = Pattern.compile("[,;\\s]+");

    private static void run() {
        setupDebug();
        setupTemplatesPath();

        if (ApplicationContext.getInstance().isDebug()) {
            setupReloadingClassPaths();
            setupClassReloadingPackages();
            setupClassReloadingExceptions();
            setupDebugCaptionsDir();
            setupDebugWebResourcesDir();
        }

        setupPageRequestListeners();
        setupGuiceModuleClassName();
        setupSkipRegex();
        setupRequestRouter();
        setupDefaultLocale();
        setupCaptionsImplClass();
        setupCaptionFilesEncoding();
        setupAllowedLanguages();
        setupDefaultPageClassName();
        setupContextPath();
        setupResetProperties();
    }

    private static void setupResetProperties() {
        String strategy = properties.getProperty("nocturne.reset.strategy");
        if (StringUtil.isEmptyOrNull(strategy)) {
            ApplicationContext.getInstance().setResetStrategy(ResetStrategy.PERSIST);
        } else {
            ApplicationContext.getInstance().setResetStrategy(ResetStrategy.valueOf(strategy));
        }

        String resetAnnotations = properties.getProperty("nocturne.reset.reset-annotations");
        if (StringUtil.isEmptyOrNull(resetAnnotations)) {
            ApplicationContext.getInstance().setResetAnnotations(Arrays.asList(Reset.class.getName()));
        } else {
            String[] annotations = ITEMS_SPLIT_PATTERN.split(resetAnnotations);
            ApplicationContext.getInstance().setResetAnnotations(Arrays.asList(annotations));
        }

        String persistAnnotations = properties.getProperty("nocturne.reset.persist-annotations");
        if (StringUtil.isEmptyOrNull(persistAnnotations)) {
            ApplicationContext.getInstance().setPersistAnnotations(Arrays.asList(
                    Persist.class.getName(),
                    Inject.class.getName()
            ));
        } else {
            String[] annotations = ITEMS_SPLIT_PATTERN.split(persistAnnotations);
            ApplicationContext.getInstance().setPersistAnnotations(Arrays.asList(annotations));
        }
    }

    private static void setupContextPath() {
        if (properties.containsKey("nocturne.context-path")) {
            String contextPath = properties.getProperty("nocturne.context-path");
            if (contextPath != null) {
                ApplicationContext.getInstance().setContextPath(contextPath);
            }
        }
    }

    private static void setupDefaultPageClassName() {
        if (properties.containsKey("nocturne.default-page-class-name")) {
            String className = properties.getProperty("nocturne.default-page-class-name");
            if (className != null && !className.isEmpty()) {
                ApplicationContext.getInstance().setDefaultPageClassName(className);
            }
        }
    }

    private static void setupAllowedLanguages() {
        if (properties.containsKey("nocturne.allowed-languages")) {
            String languages = properties.getProperty("nocturne.allowed-languages");
            if (languages != null && !languages.isEmpty()) {
                String[] tokens = LANGUAGES_SPLIT_PATTERN.split(languages);
                List<String> list = new ArrayList<String>();
                for (String token : tokens) {
                    if (!token.isEmpty()) {
                        if (token.length() != 2) {
                            throw new ConfigurationException("nocturne.allowed-languages should contain the " +
                                    "list of 2-letters language codes separated with comma.");
                        }
                        list.add(token);
                    }
                }
                ApplicationContext.getInstance().setAllowedLanguages(list);
            }
        }
    }

    private static void setupCaptionFilesEncoding() {
        if (properties.containsKey("nocturne.caption-files-encoding")) {
            String encoding = properties.getProperty("nocturne.caption-files-encoding");
            if (encoding != null && !encoding.isEmpty()) {
                ApplicationContext.getInstance().setCaptionFilesEncoding(encoding);
            }
        }
    }

    private static void setupCaptionsImplClass() {
        if (properties.containsKey("nocturne.captions-impl-class")) {
            String clazz = properties.getProperty("nocturne.captions-impl-class");
            if (clazz != null && !clazz.isEmpty()) {
                ApplicationContext.getInstance().setCaptionsImplClass(clazz);
            }
        }
    }

    private static void setupDebugCaptionsDir() {
        if (properties.containsKey("nocturne.debug-captions-dir")) {
            String dir = properties.getProperty("nocturne.debug-captions-dir");
            if (dir != null && !dir.isEmpty()) {
                if (!new File(dir).isDirectory() && ApplicationContext.getInstance().isDebug()) {
                    throw new ConfigurationException("nocturne.debug-captions-dir property should be a directory.");
                }
                ApplicationContext.getInstance().setDebugCaptionsDir(dir);
            }
        }
    }

    private static void setupDefaultLocale() {
        if (properties.containsKey("nocturne.default-language")) {
            String language = properties.getProperty("nocturne.default-language");
            if (language != null && !language.isEmpty()) {
                if (language.length() != 2) {
                    throw new ConfigurationException("Language is expected to have exactly two letters.");
                }
                ApplicationContext.getInstance().setDefaultLocale(language);
            }
        }
    }

    private static void setupRequestRouter() {
        if (properties.containsKey("nocturne.request-router")) {
            String resolver = properties.getProperty("nocturne.request-router");
            if (resolver == null || resolver.length() == 0) {
                throw new ConfigurationException("Parameter nocturne.request-router can't be empty.");
            }
            ApplicationContext.getInstance().setRequestRouter(resolver);
        } else {
            throw new ConfigurationException("Missed parameter nocturne.request-router.");
        }
    }

    private static void setupDebugWebResourcesDir() {
        if (properties.containsKey("nocturne.debug-web-resources-dir")) {
            String dir = properties.getProperty("nocturne.debug-web-resources-dir");
            if (dir != null && dir.trim().length() > 0) {
                ApplicationContext.getInstance().setDebugWebResourcesDir(dir.trim());
            }
        }
    }

    private static void setupClassReloadingExceptions() {
        List<String> exceptions = new ArrayList<String>();
        exceptions.add(ApplicationContext.class.getName());

        if (properties.containsKey("nocturne.class-reloading-exceptions")) {
            String exceptionsAsString = properties.getProperty("nocturne.class-reloading-exceptions");
            if (exceptionsAsString != null) {
                exceptions.addAll(listOfNonEmpties(ITEMS_SPLIT_PATTERN.split(exceptionsAsString)));
            }
        }
        ApplicationContext.getInstance().setClassReloadingExceptions(exceptions);
    }

    private static void setupClassReloadingPackages() {
        List<String> packages = new ArrayList<String>();
        packages.add("org.nocturne");

        if (properties.containsKey("nocturne.class-reloading-packages")) {
            String packagesAsString = properties.getProperty("nocturne.class-reloading-packages");
            if (packagesAsString != null) {
                packages.addAll(listOfNonEmpties(ITEMS_SPLIT_PATTERN.split(packagesAsString)));
            }
        }
        ApplicationContext.getInstance().setClassReloadingPackages(packages);
    }

    private static void setupSkipRegex() {
        if (properties.containsKey("nocturne.skip-regex")) {
            String regex = properties.getProperty("nocturne.skip-regex");
            if (regex != null && !regex.isEmpty()) {
                try {
                    ApplicationContext.getInstance().setSkipRegex(Pattern.compile(regex));
                } catch (PatternSyntaxException e) {
                    throw new ConfigurationException("Parameter nocturne.skip-regex contains invalid pattern.", e);
                }
            }
        }
    }

    private static void setupGuiceModuleClassName() {
        if (properties.containsKey("nocturne.guice-module-class-name")) {
            String module = properties.getProperty("nocturne.guice-module-class-name");
            if (module != null && !module.isEmpty()) {
                ApplicationContext.getInstance().setGuiceModuleClassName(module);
            }
        }
    }

    private static void setupPageRequestListeners() {
        List<String> listeners = new ArrayList<String>();
        if (properties.containsKey("nocturne.page-request-listeners")) {
            String pageRequestListenersAsString = properties.getProperty("nocturne.page-request-listeners");
            if (pageRequestListenersAsString != null) {
                listeners.addAll(listOfNonEmpties(ITEMS_SPLIT_PATTERN.split(pageRequestListenersAsString)));
            }
        }
        ApplicationContext.getInstance().setPageRequestListeners(listeners);
    }

    private static void setupReloadingClassPaths() {
        List<File> reloadingClassPaths = new ArrayList<File>();
        if (properties.containsKey("nocturne.reloading-class-paths")) {
            String reloadingClassPathsAsString = properties.getProperty("nocturne.reloading-class-paths");
            if (reloadingClassPathsAsString != null) {
                String[] dirs = ITEMS_SPLIT_PATTERN.split(reloadingClassPathsAsString);
                for (String dir : dirs) {
                    if (dir != null && !dir.isEmpty()) {
                        File file = new File(dir);
                        if (!file.isDirectory() && ApplicationContext.getInstance().isDebug()) {
                            throw new ConfigurationException("Each item in nocturne.reloading-class-paths should be a directory.");
                        }
                        reloadingClassPaths.add(file);
                    }
                }
            }
        }
        ApplicationContext.getInstance().setReloadingClassPaths(reloadingClassPaths);
    }

    private static void setupTemplatesPath() {
        if (properties.containsKey("nocturne.templates-path")) {
            String templatesPath = properties.getProperty("nocturne.templates-path");
            if (templatesPath == null || templatesPath.length() == 0) {
                throw new ConfigurationException("Parameter nocturne.templates-path can't be empty.");
            }
            ApplicationContext.getInstance().setTemplatesPath(templatesPath);
        } else {
            throw new ConfigurationException("Missed parameter nocturne.templates-path.");
        }
    }

    private static void setupDebug() {
        ApplicationContext.getInstance().setDebug(Boolean.parseBoolean(properties.getProperty("nocturne.debug")));
    }

    private static List<String> listOfNonEmpties(String[] strings) {
        List<String> result = new ArrayList<String>(strings.length);
        for (String s : strings) {
            if (!StringUtil.isEmptyOrNull(s)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Scans classpath for modules.
     *
     * @return List of modules ordered by priority (from high priority to low).
     */
    private static List<Module> getModulesFromClasspath() {
        List<Module> modules = new ArrayList<Module>();
        URLClassLoader loader = (URLClassLoader) ApplicationContext.class.getClassLoader();
        URL[] classPath = loader.getURLs();
        for (URL url : classPath) {
            if (Module.isModuleUrl(url)) {
                modules.add(new Module(url));
            }
        }
        return modules;
    }

    /**
     * Runs init() method for all modules.
     * Each module should be initialized on the application startup.
     */
    private static void initializeModules() {
        List<Module> modules = getModulesFromClasspath();

        for (Module module : modules) {
            module.init();
        }

        Collections.sort(modules, new Comparator<Module>() {
            @Override
            public int compare(Module o1, Module o2) {
                int priorityComparisonResult = Integer.valueOf(o2.getPriority()).compareTo(o1.getPriority());
                return priorityComparisonResult == 0 ? o1.getName().compareTo(o2.getName()) : priorityComparisonResult;
            }
        });

        for (Module module : modules) {
            module.getConfiguration().addPages();
        }

        ApplicationContext.getInstance().setModules(modules);
    }

    private static void setupInjector() {
        String guiceModuleClassName = ApplicationContext.getInstance().getGuiceModuleClassName();
        GenericIocModule module = new GenericIocModule();

        if (guiceModuleClassName != null) {
            try {
                com.google.inject.Module applicationModule = (com.google.inject.Module) ApplicationContext.class.getClassLoader().loadClass(
                        guiceModuleClassName
                ).getConstructor().newInstance();
                module.setModule(applicationModule);
            } catch (Exception e) {
                throw new ConfigurationException("Can't load application guice module.", e);
            }
        }

        Injector injector = Guice.createInjector(module);

        if (ApplicationContext.getInstance().isDebug()) {
            try {
                Method method = ApplicationContext.class.getDeclaredMethod("setInjector", Injector.class);
                method.setAccessible(true);
                method.invoke(ApplicationContext.getInstance(), injector);
            } catch (NoSuchMethodException e) {
                throw new NocturneException("Can't find method setInjector.", e);
            } catch (InvocationTargetException e) {
                throw new NocturneException("InvocationTargetException", e);
            } catch (IllegalAccessException e) {
                throw new NocturneException("IllegalAccessException", e);
            }
        } else {
            ApplicationContext.getInstance().setInjector(injector);
        }
    }

    private static void runModuleStartups() {
        List<Module> modules = ApplicationContext.getInstance().getModules();
        for (Module module : modules) {
            String startupClassName = module.getStartupClassName();
            if (!startupClassName.isEmpty()) {
                Runnable runnable;
                try {
                    runnable = (Runnable) ApplicationContext.getInstance().getInjector().getInstance(
                            ApplicationContext.class.getClassLoader().loadClass(startupClassName));
                } catch (ClassCastException e) {
                    throw new ModuleInitializationException("Startup class " + startupClassName
                            + " must implement Runnable.", e);
                } catch (ClassNotFoundException e) {
                    throw new ModuleInitializationException("Can't load startup class be name "
                            + startupClassName + '.', e);
                }
                if (runnable != null) {
                    runnable.run();
                }
            }
        }
    }

    static void initialize() {
        synchronized (ApplicationContextLoader.class) {
            run();
            initializeModules();
            setupInjector();
            runModuleStartups();
            ApplicationContext.getInstance().setInitialized();
        }
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
                } catch (IOException ignored) {
                    // No operations.
                }
            }
        }
    }
}