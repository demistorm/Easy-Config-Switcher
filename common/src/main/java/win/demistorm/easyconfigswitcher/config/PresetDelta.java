package win.demistorm.easyconfigswitcher.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PresetDelta {

    public int formatVersion = 1;
    public String base;
    public List<String> deletedFiles = new ArrayList<>();
    public Map<String, ListDelta> listDeltas = new LinkedHashMap<>();
    public Map<String, String> ancestorHashes = new LinkedHashMap<>();

    public static final class ListDelta {
        public List<String> added = new ArrayList<>();
        public List<String> removed = new ArrayList<>();
        public List<String> orderedOverride;

        public boolean isEmpty() {
            return (added == null || added.isEmpty())
                    && (removed == null || removed.isEmpty())
                    && orderedOverride == null;
        }
    }
}
