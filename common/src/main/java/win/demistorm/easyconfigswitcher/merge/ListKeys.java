package win.demistorm.easyconfigswitcher.merge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ListKeys {

    public static final Set<String> KEYS = Set.of("resourcePacks", "incompatibleResourcePacks");

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();

    private ListKeys() {
    }

    public static boolean isListKey(String key) {
        return KEYS.contains(key);
    }

    public static List<String> parse(String rawValue) {
        if (rawValue == null) return List.of();
        try {
            List<String> list = GSON.fromJson(rawValue, LIST_TYPE);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return null;
        }
    }

    public static String write(List<String> list) {
        return GSON.toJson(list, LIST_TYPE);
    }

    public static boolean sameSet(List<String> a, List<String> b) {
        return new HashSet<>(a).equals(new HashSet<>(b));
    }

    public static List<String> applyDelta(List<String> base, List<String> added, List<String> removed) {
        List<String> out = new ArrayList<>();
        for (String entry : base) {
            if (removed == null || !removed.contains(entry)) {
                out.add(entry);
            }
        }
        if (added != null) {
            for (String entry : added) {
                if (!out.contains(entry)) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    public static List<String> difference(List<String> from, List<String> without) {
        List<String> out = new ArrayList<>();
        for (String entry : from) {
            if (!without.contains(entry) && !out.contains(entry)) {
                out.add(entry);
            }
        }
        return out;
    }
}
