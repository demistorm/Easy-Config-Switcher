package win.demistorm.easyconfigswitcher.merge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListKeysTest {

    @Test
    void parsesVanillaFormat() {
        List<String> list = ListKeys.parse("[\"vanilla\",\"file/Fast Better Grass.zip\"]");
        assertEquals(List.of("vanilla", "file/Fast Better Grass.zip"), list);
    }

    @Test
    void roundTripsEscapedEntries() {
        String raw = "[\"vanilla\",\"file/§9Drodi\\u0027s Illagers x FA [v5.2](1).zip\"]";
        List<String> parsed = ListKeys.parse(raw);
        assertEquals("file/§9Drodi's Illagers x FA [v5.2](1).zip", parsed.get(1));
        assertEquals(raw, ListKeys.write(parsed));
    }

    @Test
    void roundTripsCommaInName() {
        List<String> list = List.of("vanilla", "file/pack, with comma.zip");
        assertEquals(list, ListKeys.parse(ListKeys.write(list)));
    }

    @Test
    void nullAndEmptyParseToEmptyList() {
        assertEquals(List.of(), ListKeys.parse(null));
        assertEquals(List.of(), ListKeys.parse(""));
    }

    @Test
    void unparseableReturnsNull() {
        assertNull(ListKeys.parse("not a json list"));
    }

    @Test
    void applyDeltaRemovesAndAdds() {
        List<String> result = ListKeys.applyDelta(
                List.of("vanilla", "file/Realistic.zip", "file/Keep.zip"),
                List.of("file/Stylized.zip"),
                List.of("file/Realistic.zip"));
        assertEquals(List.of("vanilla", "file/Keep.zip", "file/Stylized.zip"), result);
    }

    @Test
    void sameSetDetectsReorder() {
        assertTrue(ListKeys.sameSet(List.of("a", "b"), List.of("b", "a")));
        assertFalse(ListKeys.sameSet(List.of("a", "b"), List.of("a", "c")));
    }

    @Test
    void differencePreservesOrderAndDedupes() {
        List<String> diff = ListKeys.difference(List.of("a", "b", "a", "c"), List.of("b"));
        assertEquals(List.of("a", "c"), diff);
    }
}
