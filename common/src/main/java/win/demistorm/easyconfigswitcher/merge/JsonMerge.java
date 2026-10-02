package win.demistorm.easyconfigswitcher.merge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

public final class JsonMerge {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonMerge() {
    }

    public static boolean canMerge(byte[] ancestor, byte[] ours, byte[] theirs) {
        return parseObject(ancestor) != null && parseObject(ours) != null && parseObject(theirs) != null;
    }

    public static byte[] merge(byte[] ancestor, byte[] ours, byte[] theirs) {
        JsonObject merged = mergeObjects(parseObject(ancestor), parseObject(ours), parseObject(theirs));
        return (GSON.toJson(merged) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject parseObject(byte[] bytes) {
        if (bytes == null) return null;
        try {
            JsonElement el = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject mergeObjects(JsonObject a, JsonObject o, JsonObject t) {
        Set<String> keys = new LinkedHashSet<>();
        if (a != null) a.keySet().forEach(keys::add);
        if (o != null) o.keySet().forEach(keys::add);
        if (t != null) t.keySet().forEach(keys::add);

        JsonObject out = new JsonObject();
        for (String key : keys) {
            JsonElement av = a != null ? a.get(key) : null;
            JsonElement ov = o != null ? o.get(key) : null;
            JsonElement tv = t != null ? t.get(key) : null;

            if (ov == null && tv == null) continue;
            if (ov == null) {
                if (av != null && tv.equals(av)) continue;
                out.add(key, tv);
                continue;
            }
            if (tv == null) {
                if (av != null) continue;
                out.add(key, ov);
                continue;
            }
            if (ov.equals(tv)) {
                out.add(key, tv);
                continue;
            }
            if (av == null) {
                if (ov.isJsonObject() && tv.isJsonObject()) {
                    out.add(key, mergeObjects(null, ov.getAsJsonObject(), tv.getAsJsonObject()));
                } else {
                    out.add(key, tv);
                }
                continue;
            }
            if (av.equals(ov)) {
                out.add(key, tv);
                continue;
            }
            if (av.equals(tv)) {
                out.add(key, ov);
                continue;
            }
            if (ov.isJsonObject() && tv.isJsonObject() && av.isJsonObject()) {
                out.add(key, mergeObjects(av.getAsJsonObject(), ov.getAsJsonObject(), tv.getAsJsonObject()));
            } else {
                out.add(key, tv);
            }
        }
        return out;
    }
}
