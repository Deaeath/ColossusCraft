package adris.altoclef.util.helpers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

public interface StlHelper {
    static <T> Comparator<T> compareValues(Function<T, Double> getValue) {
        return Comparator.comparingDouble(getValue::apply);
    }

    static <T> String toString(Collection<T> thing, Function<T, String> toStringFunc) {
        StringBuilder result = new StringBuilder("[");
        int i = 0;
        for (T item : thing) {
            result.append(toStringFunc.apply(item));
            if (i != thing.size() - 1) result.append(",");
            ++i;
        }
        return result.append("]").toString();
    }

    static <T> String toString(T[] thing, Function<T, String> toStringFunc) {
        return toString(Arrays.asList(thing), toStringFunc);
    }
}
