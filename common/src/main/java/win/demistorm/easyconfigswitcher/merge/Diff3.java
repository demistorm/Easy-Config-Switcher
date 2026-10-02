package win.demistorm.easyconfigswitcher.merge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Diff3 {

    public record Result(List<String> merged, int conflicts) {
    }

    private Diff3() {
    }

    public static Result merge(List<String> base, List<String> ours, List<String> theirs, LineDiffer differ) {
        List<LineDiffer.LineDelta> a = new ArrayList<>(differ.diff(base, ours));
        List<LineDiffer.LineDelta> b = new ArrayList<>(differ.diff(base, theirs));
        a.sort(Comparator.comparingInt(LineDiffer.LineDelta::start));
        b.sort(Comparator.comparingInt(LineDiffer.LineDelta::start));

        List<int[]> chunks = buildChunks(a, b);

        List<String> out = new ArrayList<>(base.size());
        int pos = 0;
        int aCur = 0;
        int bCur = 0;
        int aIdx = 0;
        int bIdx = 0;
        int conflicts = 0;

        for (int[] chunk : chunks) {
            int s = chunk[0];
            int e = chunk[1];
            boolean zero = s == e;

            int unchanged = Math.max(0, s - pos);
            addRange(out, base, pos, s);
            aCur += unchanged;
            bCur += unchanged;

            int aStart = aCur;
            int bStart = bCur;
            int aLen = e - s;
            int bLen = e - s;
            while (aIdx < a.size()) {
                LineDiffer.LineDelta d = a.get(aIdx);
                if (d.start() >= e && !(zero && d.start() == e)) break;
                aIdx++;
                aLen += d.replacement().size() - (d.end() - d.start());
            }
            while (bIdx < b.size()) {
                LineDiffer.LineDelta d = b.get(bIdx);
                if (d.start() >= e && !(zero && d.start() == e)) break;
                bIdx++;
                bLen += d.replacement().size() - (d.end() - d.start());
            }
            int aEnd = aStart + aLen;
            int bEnd = bStart + bLen;

            List<String> baseImg = safeSub(base, s, e);
            List<String> aImg = safeSub(ours, aStart, aEnd);
            List<String> bImg = safeSub(theirs, bStart, bEnd);

            if (aImg.equals(baseImg)) {
                out.addAll(bImg);
            } else if (bImg.equals(baseImg) || aImg.equals(bImg)) {
                out.addAll(aImg);
            } else if (baseImg.isEmpty()) {
                out.addAll(unionInserts(aImg, bImg));
                conflicts++;
            } else {
                out.addAll(bImg);
                conflicts++;
            }

            pos = e;
            aCur = aEnd;
            bCur = bEnd;
        }

        addRange(out, base, pos, base.size());
        return new Result(out, conflicts);
    }

    private static List<String> unionInserts(List<String> ours, List<String> theirs) {
        Set<String> theirsLines = new HashSet<>(theirs);
        Set<String> theirsKeys = new HashSet<>();
        for (String line : theirs) {
            String key = keyOf(line);
            if (key != null) theirsKeys.add(key);
        }
        List<String> out = new ArrayList<>(ours.size() + theirs.size());
        for (String line : ours) {
            if (theirsLines.contains(line)) continue;
            String key = keyOf(line);
            if (key != null && theirsKeys.contains(key)) continue;
            out.add(line);
        }
        out.addAll(theirs);
        return out;
    }

    private static String keyOf(String line) {
        Matcher m = KEY_PATTERN.matcher(line);
        if (!m.find()) return null;
        String key = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        return key.trim();
    }

    private static final Pattern KEY_PATTERN = Pattern.compile("^\\s*(?:\"([^\"]+)\"|'([^']+)'|([^=:]+?))\\s*[=:]");

    private static List<int[]> buildChunks(List<LineDiffer.LineDelta> a, List<LineDiffer.LineDelta> b) {
        List<int[]> intervals = new ArrayList<>();
        for (LineDiffer.LineDelta d : a) intervals.add(new int[]{d.start(), d.end()});
        for (LineDiffer.LineDelta d : b) intervals.add(new int[]{d.start(), d.end()});
        intervals.sort(Comparator.<int[]>comparingInt(i -> i[0]).thenComparingInt(i -> i[1]));

        List<int[]> chunks = new ArrayList<>();
        for (int[] interval : intervals) {
            if (!chunks.isEmpty()) {
                int[] last = chunks.get(chunks.size() - 1);
                if (interval[0] < last[1]) {
                    last[1] = Math.max(last[1], interval[1]);
                    continue;
                }
            }
            chunks.add(new int[]{interval[0], interval[1]});
        }
        return chunks;
    }

    private static List<String> safeSub(List<String> list, int from, int to) {
        int f = Math.max(0, Math.min(list.size(), from));
        int t = Math.max(f, Math.min(list.size(), to));
        return new ArrayList<>(list.subList(f, t));
    }

    private static void addRange(List<String> out, List<String> list, int from, int to) {
        int f = Math.max(0, Math.min(list.size(), from));
        int t = Math.max(f, Math.min(list.size(), to));
        for (int i = f; i < t; i++) {
            out.add(list.get(i));
        }
    }
}
