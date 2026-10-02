package win.demistorm.easyconfigswitcher.merge;

import java.util.List;

public interface LineDiffer {

    record LineDelta(int start, int end, List<String> replacement) {
    }

    List<LineDelta> diff(List<String> original, List<String> revised);
}
