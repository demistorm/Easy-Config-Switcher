package win.demistorm.easyconfigswitcher.merge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMergeTest {

    private byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private JsonObject parse(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void gsonStyleEndOfObjectAdditionsMerge() {
        String ancestor = "{\n  \"entities\": {\n    \"chicken\": \"E1\"\n  }\n}";
        String ours = "{\n  \"entities\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\",\n    \"pig\": \"E3\"\n  }\n}";
        String theirs = "{\n  \"entities\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\"\n  }\n}";

        assertTrue(JsonMerge.canMerge(b(ancestor), b(ours), b(theirs)));
        JsonObject merged = parse(JsonMerge.merge(b(ancestor), b(ours), b(theirs)));

        JsonObject entities = merged.getAsJsonObject("entities");
        assertEquals(3, entities.size());
        assertEquals("E1", entities.get("chicken").getAsString());
        assertEquals("E2", entities.get("cow").getAsString());
        assertEquals("E3", entities.get("pig").getAsString());
    }

    @Test
    void oursDeletionOfTheirsUnchangedKeyHolds() {
        String ancestor = "{\n  \"e\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\"\n  }\n}";
        String ours = "{\n  \"e\": {\n    \"chicken\": \"E1\"\n  }\n}";
        String theirs = "{\n  \"e\": {\n    \"chicken\": \"E1\",\n    \"cow\": \"E2\",\n    \"pig\": \"E3\"\n  }\n}";

        JsonObject merged = parse(JsonMerge.merge(b(ancestor), b(ours), b(theirs)));
        JsonObject e = merged.getAsJsonObject("e");
        assertFalse(e.has("cow"));
        assertEquals("E3", e.get("pig").getAsString());
    }

    @Test
    void scalarConflictTheirsWins() {
        String ancestor = "{\"a\": 1}";
        String ours = "{\"a\": 2}";
        String theirs = "{\"a\": 3}";

        JsonObject merged = parse(JsonMerge.merge(b(ancestor), b(ours), b(theirs)));
        assertEquals(3, merged.get("a").getAsInt());
    }

    @Test
    void nonObjectInputCannotMerge() {
        assertFalse(JsonMerge.canMerge(b("[1,2]"), b("[1,2]"), b("[1,2]")));
        assertFalse(JsonMerge.canMerge(b("not json"), b("{}"), b("{}")));
    }
}
