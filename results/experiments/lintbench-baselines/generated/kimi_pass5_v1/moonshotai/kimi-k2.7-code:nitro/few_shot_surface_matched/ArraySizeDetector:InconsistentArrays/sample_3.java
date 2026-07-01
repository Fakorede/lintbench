package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    private static final String ARRAY = "array";
    private static final String STRING_ARRAY = "string-array";
    private static final String INTEGER_ARRAY = "integer-array";
    private static final String ITEM = "item";
    private static final String ATTR_NAME = "name";

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these. "
                            + "Note however that there may be cases where you really want to declare "
                            + "a different number of array items in each configuration, so use your "
                            + "own judgment to decide if this is really an error. You can suppress "
                            + "this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private Map<String, List<ArrayOccurrence>> mOccurrences;

    private static class ArrayOccurrence {
        final int count;
        final Location location;

        ArrayOccurrence(int count, Location location) {
            this.count = count;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(ARRAY, STRING_ARRAY, INTEGER_ARRAY);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mOccurrences = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        int count = countItems(element);
        List<ArrayOccurrence> occurrences = mOccurrences.get(name);
        if (occurrences == null) {
            occurrences = new ArrayList<>();
            mOccurrences.put(name, occurrences);
        }
        occurrences.add(new ArrayOccurrence(count, context.getLocation(element)));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ArrayOccurrence>> entry : mOccurrences.entrySet()) {
            List<ArrayOccurrence> occurrences = entry.getValue();
            if (occurrences.size() < 2) {
                continue;
            }

            int expected = occurrences.get(0).count;
            boolean consistent = true;
            for (int i = 1; i < occurrences.size(); i++) {
                if (occurrences.get(i).count != expected) {
                    consistent = false;
                    break;
                }
            }
            if (consistent) {
                continue;
            }

            for (ArrayOccurrence occurrence : occurrences) {
                if (occurrence.count != expected) {
                    context.report(
                            ISSUE,
                            occurrence.location,
                            "Array \""
                                    + entry.getKey()
                                    + "\" has "
                                    + occurrence.count
                                    + " items here, but "
                                    + expected
                                    + " in another configuration");
                }
            }
        }

        mOccurrences = null;
    }

    private static int countItems(@NonNull Element element) {
        int count = 0;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && ITEM.equals(child.getNodeName())) {
                count++;
            }
            child = child.getNextSibling();
        }
        return count;
    }
}