package com.github.manolo8.darkbot.extensions.features;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.extensions.plugins.Plugin;
import com.github.manolo8.darkbot.extensions.plugins.PluginHandler;
import com.github.manolo8.darkbot.extensions.plugins.PluginListener;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FeatureRegistry implements PluginListener {

    private final PluginHandler pluginHandler;
    private final Map<String, FeatureDefinition<?>> FEATURES_BY_ID = new LinkedHashMap<>();
    private final FeatureInstanceLoader featureLoader;
    private final FeatureRegisterHandler registryHandler;

    public FeatureRegistry(Main main, PluginHandler pluginHandler) {
        this.pluginHandler = pluginHandler;
        this.featureLoader = new FeatureInstanceLoader(main);
        this.registryHandler = new FeatureRegisterHandler(main, this);
        pluginHandler.addListener(this);
    }

    @Override
    public void beforeLoad() {
        FEATURES_BY_ID.values()
                .stream()
                .map(FeatureDefinition::getInstance)
                .filter(Objects::nonNull)
                .forEach(featureLoader::unloadFeature);
        FEATURES_BY_ID.clear();
        registryHandler.getNativeFeatures().forEach(this::registerNativeFeature);
        registryHandler.update();
    }

    @Override
    public void afterLoad() {
        pluginHandler.LOADED_PLUGINS.forEach(pl ->
                Arrays.stream(pl.getDefinition().features)
                        .forEach(feature -> registerPluginFeature(pl, feature)));
        registryHandler.update();
    }

    private void registerNativeFeature(Class<?> clazz) {
        FEATURES_BY_ID.put(clazz.getCanonicalName(), new FeatureDefinition<>(null, clazz));
    }

    private void registerPluginFeature(Plugin plugin, String clazzName) {
        try {
            Class<?> feature = pluginHandler.PLUGIN_CLASS_LOADER.loadClass(clazzName);
            FeatureDefinition<?> fd = new FeatureDefinition<>(plugin, feature);
            fd.addStatusListener(def -> registryHandler.update());
            fd.getIssues().addListener(iss -> registryHandler.update());
            FEATURES_BY_ID.put(clazzName, fd);
        } catch (ClassNotFoundException e) {
            plugin.getIssues().addWarning("Feature failed to load", clazzName + " couldn't be registered properly: " + e.getMessage());
        }
    }

    private <T> Optional<T> getFeature(String id) {
        synchronized (pluginHandler) {
            FeatureDefinition<T> feature = getFeatureDefinition(id);
            try {
                if (feature == null || !feature.canLoad()) return Optional.empty();

                T instance = feature.getInstance();
                if (instance != null) return Optional.of(instance);

                feature.setInstance(instance = featureLoader.loadFeature(feature));
                return Optional.of(instance);
            } catch (Exception e) {
                feature.getIssues().addFailure("Failed to load", Stream.concat(
                        e.getMessage() == null ? Stream.empty() : Stream.of("<strong>" + e.getMessage() + "</strong>"),
                        Arrays.stream(e.getStackTrace())
                ).map(Objects::toString).collect(Collectors.joining("<br>", "<html>", "</html>")));
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    public <T> Optional<T> getFeature(String id, Class<T> type) {
        return getFeature(id);
    }

    public <T> Optional<T> getFeature(FeatureDefinition<T> fd) {
        return getFeature(fd.getId());
    }

    public <T> Stream<FeatureDefinition<T>> getFeatures(Class<T> type) {
        //noinspection unchecked
        return FEATURES_BY_ID
                .values()
                .stream()
                .filter(FeatureDefinition::canLoad)
                .filter(fd -> type.isAssignableFrom(fd.getClazz()))
                .map(fd -> (FeatureDefinition<T>) fd);
    }

    public Stream<FeatureDefinition<?>> getFeatures(Plugin plugin) {
        return FEATURES_BY_ID
                .values()
                .stream()
                .filter(fd -> fd.getPlugin() == plugin);
    }


    public <T> FeatureDefinition<T> getFeatureDefinition(String id) {
        synchronized (pluginHandler) {
            //noinspection unchecked
            return (FeatureDefinition<T>) FEATURES_BY_ID.get(id);
        }
    }

}