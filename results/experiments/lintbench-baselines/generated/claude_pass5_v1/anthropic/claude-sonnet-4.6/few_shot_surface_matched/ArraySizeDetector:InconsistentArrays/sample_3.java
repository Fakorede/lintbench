package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
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
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from array name to a list of (file, count) pairs recording how many items
     * each file declares for that array.
     */
    private Map<String, List<int[]>> mArrayCount;

    /**
     * Map from array name to a list of XmlContext objects for location reporting.
     */
    private Map<String, List<XmlContext>> mArrayContexts;

    /**
     * Map from array name to a list of Element nodes for location reporting.
     */
    private Map<String, List<Element>> mArrayElements;

    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        mArrayCount = new HashMap<>();
        mArrayContexts = new HashMap<>();
        mArrayElements = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mArrayCount == null) {
            return;
        }

        for (Map.Entry<String, List<int[]>> entry : mArrayCount.entrySet()) {
            String name = entry.getKey();
            List<int[]> counts = entry.getValue();

            if (counts.size() < 2) {
                continue;
            }

            // Find the reference count (from the default locale if available, else first)
            int referenceCount = -1;
            int referenceIndex = -1;

            List<XmlContext> contexts = mArrayContexts.get(name);
            List<Element> elements = mArrayElements.get(name);

            // Try to find a default values folder (no qualifiers)
            for (int i = 0; i < counts.size(); i++) {
                XmlContext ctx = contexts.get(i);
                File file = ctx.file;
                File folder = file.getParentFile();
                String folderName = folder != null ? folder.getName() : "";
                if (folderName.equals("values")) {
                    referenceCount = counts.get(i)[0];
                    referenceIndex = i;
                    break;
                }
            }

            if (referenceIndex == -1) {
                // No default locale found; use first entry as reference
                referenceCount = counts.get(0)[0];
                referenceIndex = 0;
            }

            // Check all other entries against the reference count
            for (int i = 0; i < counts.size(); i++) {
                if (i == referenceIndex) {
                    continue;
                }
                int count = counts.get(i)[0];
                if (count != referenceCount) {
                    XmlContext xmlContext = contexts.get(i);
                    Element element = elements.get(i);

                    XmlContext refContext = contexts.get(referenceIndex);
                    File refFile = refContext.file;

                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in this file, "
                                    + "%3$d in `%4$s`)",
                            name,
                            count,
                            referenceCount,
                            refFile.getParentFile().getName() + "/" + refFile.getName());

                    xmlContext.report(
                            ISSUE,
                            element,
                            xmlContext.getElementLocation(element),
                            message);
                }
            }
        }

        mArrayCount = null;
        mArrayContexts = null;
        mArrayElements = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Count the number of <item> children
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        List<int[]> counts = mArrayCount.get(name);
        if (counts == null) {
            counts = new ArrayList<>();
            mArrayCount.put(name, counts);
        }
        counts.add(new int[]{count});

        List<XmlContext> contexts = mArrayContexts.get(name);
        if (contexts == null) {
            contexts = new ArrayList<>();
            mArrayContexts.put(name, contexts);
        }
        contexts.add(context);

        List<Element> elements = mArrayElements.get(name);
        if (elements == null) {
            elements = new ArrayList<>();
            mArrayElements.put(name, elements);
        }
        elements.add(element);
    }
}