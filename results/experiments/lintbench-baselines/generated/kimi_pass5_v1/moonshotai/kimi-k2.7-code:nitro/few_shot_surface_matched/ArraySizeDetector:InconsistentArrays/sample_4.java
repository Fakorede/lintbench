package com.android.tools.lint.checks;

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

public class ArraySizeDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistent array sizes",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this check finds inconsistencies like these.\n\n"
                            + "Note however that there may be cases where you really want to "
                            + "declare a different number of array items in each configuration "
                            + "(for example where the array represents available options, and "
                            + "those options differ for different orientations and so on), so "
                            + "use your own judgment to decide if this is really an error.\n\n"
                            + "You can suppress this issue type if it finds false errors in your "
                            + "project.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES));

    private final Map<String, List<ArrayEntry>> mArrays = new HashMap<>();

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("array", "string-array", "integer-array");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mArrays.clear();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String folder = context.file.getParentFile().getName();
        boolean base = "values".equals(folder);
        Location location = context.getLocation(element);

        List<ArrayEntry> entries = mArrays.get(name);
        if (entries == null) {
            entries = new ArrayList<>();
            mArrays.put(name, entries);
        }
        entries.add(new ArrayEntry(name, folder, base, count, location));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (List<ArrayEntry> entries : mArrays.values()) {
            ArrayEntry baseEntry = null;
            for (ArrayEntry entry : entries) {
                if (entry.base) {
                    baseEntry = entry;
                    break;
                }
            }
            if (baseEntry == null) {
                continue;
            }

            for (ArrayEntry entry : entries) {
                if (entry == baseEntry) {
                    continue;
                }
                if (entry.count != baseEntry.count) {
                    String message =
                            String.format(
                                    "Array \"%s\" has %d items in %s but %d in %s",
                                    entry.name,
                                    baseEntry.count,
                                    baseEntry.folder,
                                    entry.count,
                                    entry.folder);
                    context.report(ISSUE, entry.location, message);
                }
            }
        }
    }

    private static class ArrayEntry {
        final String name;
        final String folder;
        final boolean base;
        final int count;
        final Location location;

        ArrayEntry(
                String name, String folder, boolean base, int count, Location location) {
            this.name = name;
            this.folder = folder;
            this.base = base;
            this.count = count;
            this.location = location;
        }
    }
}