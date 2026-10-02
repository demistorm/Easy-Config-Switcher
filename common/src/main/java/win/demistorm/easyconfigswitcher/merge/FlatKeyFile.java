package win.demistorm.easyconfigswitcher.merge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlatKeyFile {

    private FlatKeyFile() {
    }

    public static boolean wellFormed(List<String> lines) {
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            int i = line.indexOf(':');
            if (i <= 0) return false;
        }
        return true;
    }

    public static LinkedHashMap<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null) continue;
            int i = line.indexOf(':');
            if (i <= 0) continue;
            String key = line.substring(0, i);
            String value = line.substring(i + 1);
            map.put(key, value);
        }
        return map;
    }

    public static List<String> serialize(Map<String, String> map) {
        List<String> lines = new ArrayList<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            lines.add(entry.getKey() + ":" + entry.getValue());
        }
        return lines;
    }

    public static LinkedHashMap<String, String> merge(Map<String, String> base, Map<String, String> overrides) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }
}
