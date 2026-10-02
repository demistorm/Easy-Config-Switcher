package win.demistorm.easyconfigswitcher.merge;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatKeyFileTest {

    @Test
    void parsesFirstColonOnly() {
        LinkedHashMap<String, String> map = FlatKeyFile.parse(List.of("renderDistance:12", "fullscreenResolution:2560x1440@60"));
        assertEquals("12", map.get("renderDistance"));
        assertEquals("2560x1440@60", map.get("fullscreenResolution"));
    }

    @Test
    void serializesRoundTrip() {
        List<String> lines = List.of("a:1", "b:x:y", "c:[\"vanilla\"]");
        assertEquals(lines, FlatKeyFile.serialize(FlatKeyFile.parse(lines)));
    }

    @Test
    void wellFormedAcceptsBlankLines() {
        assertTrue(FlatKeyFile.wellFormed(List.of("a:1", "", "b:2")));
        assertFalse(FlatKeyFile.wellFormed(List.of("a:1", "# comment")));
        assertFalse(FlatKeyFile.wellFormed(List.of("no colon here")));
    }

    @Test
    void mergeKeepsBaseOrderOverridesValues() {
        Map<String, String> base = FlatKeyFile.parse(List.of("a:1", "b:2", "c:3"));
        Map<String, String> overrides = FlatKeyFile.parse(List.of("b:9", "d:4"));
        LinkedHashMap<String, String> merged = FlatKeyFile.merge(base, overrides);
        assertEquals(List.of("a:1", "b:9", "c:3", "d:4"), FlatKeyFile.serialize(merged));
    }

    @Test
    void duplicateKeysLastValueWins() {
        LinkedHashMap<String, String> map = FlatKeyFile.parse(List.of("k:1", "k:2"));
        assertEquals("2", map.get("k"));
    }
}
