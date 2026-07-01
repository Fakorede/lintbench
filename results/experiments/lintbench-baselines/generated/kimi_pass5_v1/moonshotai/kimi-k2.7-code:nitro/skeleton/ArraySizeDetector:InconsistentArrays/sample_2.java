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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have the same number of elements as the original array. Adding or removing elements in one configuration without updating the others can lead to runtime errors, and this check finds such inconsistencies.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<Entry>> mEntries;

    private static class Entry {
        final int count;
        final Location location;
        final String name;

        Entry(int count, Location location, String name) {
            this.count = count;
            this.location = location;
            this.name = name;
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
        mEntries = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String key = element.getTagName() + "/" + name;
        List<Entry> entries = mEntries.get(key);
        if (entries == null) {
            entries = new ArrayList<>();
            mEntries.put(key, entries);
        }
        entries.add(new Entry(count, context.getLocation(element), name));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (List<Entry> entries : mEntries.values()) {
            if (entries.size() < 2) {
                continue;
            }

            int expected = entries.get(0).count;
            boolean consistent = true;
            for (Entry entry : entries) {
                if (entry.count != expected) {
                    consistent = false;
                    break;
                }
            }

            if (!consistent) {
                StringBuilder sizes = new StringBuilder();
                for (int i = 0; i < entries.size(); i++) {
                    if (i > 0) {
                        sizes.append(", ");
                    }
                    sizes.append(entries.get(i).count);
                }

                for (Entry entry : entries) {
                    String message =
                            "Array \""
                                    + entry.name
                                    + "\" has "
                                    + entry.count
                                    + " items here, but other configurations have sizes: "
                                    + sizes;
                    context.report(ISSUE, entry.location, message);
                }
            }
        }
    }
}