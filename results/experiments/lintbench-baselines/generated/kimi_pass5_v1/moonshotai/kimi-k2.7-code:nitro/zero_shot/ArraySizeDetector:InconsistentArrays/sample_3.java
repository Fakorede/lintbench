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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have "
                    + "the same number of elements as the original array. When adding or "
                    + "removing elements to an array, it is easy to forget to update all "
                    + "the locales, and this lint warning finds inconsistencies like these.\n\n"
                    + "Note however that there may be cases where you really want to declare "
                    + "a different number of array items in each configuration (for example "
                    + "where the array represents available options, and those options differ "
                    + "for different layout orientations and so on), so use your own judgment "
                    + "to decide if this is really an error.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE))
    );

    private static final String STRING_ARRAY = "string-array";
    private static final String INTEGER_ARRAY = "integer-array";
    private static final String ARRAY = "array";

    private Map<String, List<Occurrence>> mOccurrences;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(STRING_ARRAY, INTEGER_ARRAY, ARRAY);
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mOccurrences = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int itemCount = countItemElements(element);
        String folder = context.file.getParentFile().getName();
        String key = element.getTagName() + "/" + name;

        List<Occurrence> occurrences = mOccurrences.get(key);
        if (occurrences == null) {
            occurrences = new ArrayList<>();
            mOccurrences.put(key, occurrences);
        }
        occurrences.add(new Occurrence(folder, itemCount, context.getLocation(element)));
    }

    private static int countItemElements(@NonNull Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<Occurrence>> entry : mOccurrences.entrySet()) {
            List<Occurrence> occurrences = entry.getValue();
            if (occurrences.size() < 2) {
                continue;
            }

            int expected = occurrences.get(0).count;
            boolean consistent = true;
            for (Occurrence occurrence : occurrences) {
                if (occurrence.count != expected) {
                    consistent = false;
                    break;
                }
            }
            if (consistent) {
                continue;
            }

            occurrences.sort(Comparator.comparing(o -> o.folder));

            StringBuilder message = new StringBuilder();
            message.append("Array ").append(entry.getKey()).append(" has inconsistent item counts: ");
            for (int i = 0, n = occurrences.size(); i < n; i++) {
                Occurrence occurrence = occurrences.get(i);
                message.append(occurrence.folder).append('=').append(occurrence.count);
                if (i < n - 1) {
                    message.append(", ");
                }
            }

            Location location = occurrences.get(0).location;
            Location current = location;
            for (int i = 1, n = occurrences.size(); i < n; i++) {
                Location next = occurrences.get(i).location;
                current.setSecondary(next);
                current = next;
            }

            context.report(ISSUE, location, message.toString());
        }
    }

    private static class Occurrence {
        final String folder;
        final int count;
        final Location location;

        Occurrence(String folder, int count, Location location) {
            this.folder = folder;
            this.count = count;
            this.location = location;
        }
    }
}