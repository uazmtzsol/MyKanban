package com.personalkanban.application.port;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory settings test double. */
public final class InMemorySettingsStore implements SettingsStore {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public List<String> keys(String prefix) {
        return values.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .sorted()
                .toList();
    }
}
