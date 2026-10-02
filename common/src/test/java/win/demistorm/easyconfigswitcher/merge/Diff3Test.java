package win.demistorm.easyconfigswitcher.merge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Diff3Test {

    private final LineDiffer differ = new JavaDiffUtilsLineDiffer();

    private List<String> l(String... lines) {
        return List.of(lines);
    }

    @Test
    void onlyTheirsChanged() {
        Diff3.Result r = Diff3.merge(l("A", "B", "C"), l("A", "B", "C"), l("A", "X", "C"), differ);
        assertEquals(l("A", "X", "C"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void onlyOursChanged() {
        Diff3.Result r = Diff3.merge(l("A", "B", "C"), l("A", "Y", "C"), l("A", "B", "C"), differ);
        assertEquals(l("A", "Y", "C"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void disjointChangesMerge() {
        Diff3.Result r = Diff3.merge(
                l("A", "B", "C", "D", "E"),
                l("A", "B2", "C", "D", "E"),
                l("A", "B", "C", "D", "E5"),
                differ);
        assertEquals(l("A", "B2", "C", "D", "E5"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void identicalChangesMergeOnce() {
        Diff3.Result r = Diff3.merge(l("A", "B"), l("A", "Z"), l("A", "Z"), differ);
        assertEquals(l("A", "Z"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void conflictTheirsWins() {
        Diff3.Result r = Diff3.merge(l("A", "B"), l("X", "B"), l("Y", "B"), differ);
        assertEquals(l("Y", "B"), r.merged());
        assertEquals(1, r.conflicts());
    }

    @Test
    void insertionsAtSameSpotUnion() {
        Diff3.Result r = Diff3.merge(l("A"), l("P", "A"), l("Q", "A"), differ);
        assertEquals(l("P", "Q", "A"), r.merged());
        assertEquals(1, r.conflicts());
    }

    @Test
    void oneSideInsertionOnly() {
        Diff3.Result r = Diff3.merge(l("A", "B"), l("A", "P", "B"), l("A", "B"), differ);
        assertEquals(l("A", "P", "B"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void deletionOnOneSideChangeOnOther() {
        Diff3.Result r = Diff3.merge(l("A", "B", "C"), l("A", "C"), l("A", "B", "C", "D"), differ);
        assertEquals(l("A", "C", "D"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void unionInsertsDisjointEntriesAtSameAnchor() {
        Diff3.Result r = Diff3.merge(l("A", "Z"), l("A", "b", "Z"), l("A", "c", "Z"), differ);
        assertEquals(l("A", "b", "c", "Z"), r.merged());
        assertEquals(1, r.conflicts());
    }

    @Test
    void unionInsertsDedupIdenticalLines() {
        Diff3.Result r = Diff3.merge(l("A", "Z"), l("A", "b", "Z"), l("A", "b", "Z"), differ);
        assertEquals(l("A", "b", "Z"), r.merged());
        assertEquals(0, r.conflicts());
    }

    @Test
    void unionInsertsSameKeyPresetWins() {
        Diff3.Result r = Diff3.merge(l("A", "Z"), l("A", "k=1", "Z"), l("A", "k=2", "Z"), differ);
        assertEquals(l("A", "k=2", "Z"), r.merged());
        assertEquals(1, r.conflicts());
    }

    @Test
    void unionInsertsJsonQuotedKeys() {
        Diff3.Result r = Diff3.merge(
                l("{", "  \"chicken\": \"E1\",", "  \"zzz\": true", "}"),
                l("{", "  \"chicken\": \"E1\",", "  \"pig\": \"E3\",", "  \"zzz\": true", "}"),
                l("{", "  \"chicken\": \"E1\",", "  \"cow\": \"E2\",", "  \"zzz\": true", "}"),
                differ);
        assertEquals(l("{", "  \"chicken\": \"E1\",", "  \"pig\": \"E3\",", "  \"cow\": \"E2\",", "  \"zzz\": true", "}"), r.merged());
        assertEquals(1, r.conflicts());
    }

    @Test
    void modifyConflictStillTheirsWins() {
        Diff3.Result r = Diff3.merge(l("A", "B", "C"), l("A", "X", "C"), l("A", "Y", "C"), differ);
        assertEquals(l("A", "Y", "C"), r.merged());
        assertEquals(1, r.conflicts());
    }

    @Test
    void nearbyIndependentChanges() {
        Diff3.Result r = Diff3.merge(
                l("1", "2", "3", "4", "5", "6", "7"),
                l("1", "2", "3", "FOUR", "5", "6", "7"),
                l("1", "2", "3", "4", "5", "6", "SEVEN"),
                differ);
        assertEquals(l("1", "2", "3", "FOUR", "5", "6", "SEVEN"), r.merged());
        assertEquals(0, r.conflicts());
    }
}
