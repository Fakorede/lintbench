package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArraySizeDetector extends Detector implements XmlScanner {
    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_ARRAY = "array";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TRANSLATABLE = "translatable";

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistent array sizes",
            "When an array is translated in a different locale, it should normally have the same number of elements as the original array. When adding or removing elements to an array, it is easy to forget to update all the locales, and this lint warning finds inconsistencies like these.\n\nNote however that there may be cases where you really want to declare a different number of array items in each configuration (for example where the array represents available options, and those options differ for different layout orientations and so on), so use your own judgment to decide if this is really an error.\n\nYou can suppress this error type if it finds false errors in your project.",
            Category.I18N,
            3,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, List<Entry>> mArrays = new HashMap<>();

    @Override
    @NotNull
    public List<String> getApplicableElements() {
        return Arrays.asList(TAG_STRING_ARRAY, TAG_INTEGER_ARRAY, TAG_ARRAY);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (!TAG_STRING_ARRAY.equals(tag)
                && !TAG_INTEGER_ARRAY.equals(tag)
                && !TAG_ARRAY.equals(tag)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        if ("false".equals(element.getAttribute(ATTR_TRANSLATABLE))) {
            return;
        }

        File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }

        ResourceFolderType folder = ResourceFolderType.getFolderType(parentFile.getName());
        if (folder != ResourceFolderType.VALUES) {
            return;
        }

        int count = getItemCount(element);
        String folderName = parentFile.getName();
        Location location = context.getLocation(element);

        List<Entry> entries = mArrays.get(name);
        if (entries == null) {
            entries = new ArrayList<>();
            mArrays.put(name, entries);
        }
        entries.add(new Entry(folderName, count, location));
    }

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mArrays.clear();
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, List<Entry>> mapEntry : mArrays.entrySet()) {
            String name = mapEntry.getKey();
            List<Entry> entries = mapEntry.getValue();
            if (entries.size() < 2) {
                continue;
            }

            boolean mismatch = false;
            int firstCount = entries.get(0).count;
            for (int i = 1; i < entries.size(); i++) {
                if (entries.get(i).count != firstCount) {
                    mismatch = true;
                    break;
                }
            }
            if (!mismatch) {
                continue;
            }

            for (Entry entry : entries) {
                String message = String.format(Locale.US,
                        "Array \"%s\" has %d items in %s", name, entry.count, entry.folder);
                context.report(ISSUE, entry.location, message);
            }
        }
    }

    private static int getItemCount(@NotNull Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static class Entry {
        final String folder;
        final int count;
        final Location location;

        Entry(@NotNull String folder, int count, @NotNull Location location) {
            this.folder = folder;
            this.count = count;
            this.location = location;
        }
    }
}