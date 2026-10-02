package win.demistorm.easyconfigswitcher.merge;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class JavaDiffUtilsLineDiffer implements LineDiffer {

    @Override
    public List<LineDelta> diff(List<String> original, List<String> revised) {
        List<LineDelta> out = new ArrayList<>();
        try {
            Patch<String> patch = DiffUtils.diff(original, revised);
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                int start = delta.getSource().getPosition();
                int end = start + delta.getSource().size();
                out.add(new LineDelta(start, end, new ArrayList<>(delta.getTarget().getLines())));
            }
        } catch (Exception e) {
            out.clear();
            out.add(new LineDelta(0, original.size(), new ArrayList<>(revised)));
        }
        out.sort(Comparator.comparingInt(LineDelta::start));
        return out;
    }
}
