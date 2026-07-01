package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have " +
                    "the same number of elements as the original array. When adding or removing " +
                    "elements to an array, it is easy to forget to update all the locales, and this " +
                    "lint warning finds inconsistencies like these.\n\n" +
                    "Note however that there may be cases where you really want to declare a " +
                    "different number of array items in each configuration (for example where " +
                    "the array represents available options, and those options differ for " +
                    "different layout orientations and so on), so use your own judgment to " +
                    "decide if this is really an error.\n\n" +
                    "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<ArrayEntry>> arrayCounts;

    private static class ArrayEntry {
        final int count;
        final Location location;
        final String qualifiers;

        ArrayEntry(int count, Location location, String qualifiers) {
            this.count = count;
            this.location = location;
            this.qualifiers = qualifiers;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        arrayCounts = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (arrayCounts == null) return;

        for (Map.Entry<String, List<ArrayEntry>> entry : arrayCounts.entrySet()) {
            List<ArrayEntry> entries = entry.getValue();
            if (entries.size() < 2) continue;

            int referenceCount = -1;
            // Prefer default configuration as reference
            for (ArrayEntry e : entries) {
                if (e.qualifiers.isEmpty()) {
                    referenceCount = e.count;
                    break;
                }
            }
            if (referenceCount == -1) {
                referenceCount = entries.get(0).count;
            }

            boolean hasInconsistency = false;
            for (ArrayEntry e : entries) {
                if (e.count != referenceCount) {
                    hasInconsistency = true;
                    break;
                }
            }

            if (hasInconsistency) {
                for (ArrayEntry e : entries) {
                    if (e.count != referenceCount) {
                        String message = String.format(
                                "This array has %d elements, but the default array has %d elements",
                                e.count, referenceCount);
                        context.report(ISSUE, e.location, message);
                    }
                }
            }
        }
        arrayCounts = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) return;

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String qualifiers = "";
        if (context.getFolderConfiguration() != null) {
            String q = context.getFolderConfiguration().getQualifierString();
            if (q != null) {
                qualifiers = q;
            }
        }

        Location location = context.getLocation(element);
        arrayCounts.computeIfAbsent(name, k -> new ArrayList<>())
                   .add(new ArrayEntry(count, location, qualifiers));
    }
}