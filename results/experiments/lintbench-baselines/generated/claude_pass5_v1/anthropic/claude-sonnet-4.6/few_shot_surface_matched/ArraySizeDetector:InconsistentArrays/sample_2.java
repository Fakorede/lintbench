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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or "
                            + "removing elements to an array, it is easy to forget to update all "
                            + "the locales, and this lint warning finds inconsistencies like these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to declare "
                            + "a different number of array items in each configuration (for example "
                            + "where the array represents available options, and those options differ "
                            + "for different layout orientations and so on), so use your own judgment "
                            + "to decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from array name to a pair of (count, XmlContext) for the default locale (values/).
     * Key: array name, Value: int[] { itemCount }, and we store context separately.
     */
    private final Map<String, Integer> mDefaultCounts = new HashMap<>();
    private final Map<String, XmlContext> mDefaultContexts = new HashMap<>();
    private final Map<String, Element> mDefaultElements = new HashMap<>();

    /**
     * Map from array name to list of (count, context, element) for non-default locales.
     */
    private final Map<String, java.util.List<int[]>> mOtherCounts = new HashMap<>();
    private final Map<String, java.util.List<XmlContext>> mOtherContexts = new HashMap<>();
    private final Map<String, java.util.List<Element>> mOtherElements = new HashMap<>();

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
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        mDefaultCounts.clear();
        mDefaultContexts.clear();
        mDefaultElements.clear();
        mOtherCounts.clear();
        mOtherContexts.clear();
        mOtherElements.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        // Now compare default counts with other locale counts
        for (Map.Entry<String, Integer> entry : mDefaultCounts.entrySet()) {
            String name = entry.getKey();
            int defaultCount = entry.getValue();

            java.util.List<int[]> otherCountsList = mOtherCounts.get(name);
            java.util.List<XmlContext> otherContextsList = mOtherContexts.get(name);
            java.util.List<Element> otherElementsList = mOtherElements.get(name);

            if (otherCountsList == null) {
                continue;
            }

            XmlContext defaultContext = mDefaultContexts.get(name);
            Element defaultElement = mDefaultElements.get(name);

            for (int i = 0; i < otherCountsList.size(); i++) {
                int otherCount = otherCountsList.get(i)[0];
                if (otherCount != defaultCount) {
                    XmlContext otherContext = otherContextsList.get(i);
                    Element otherElement = otherElementsList.get(i);

                    String defaultFolder = defaultContext != null
                            ? defaultContext.file.getParentFile().getName()
                            : "values";
                    String otherFolder = otherContext.file.getParentFile().getName();

                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            name,
                            otherCount,
                            otherFolder,
                            defaultCount,
                            defaultFolder);

                    otherContext.report(
                            ISSUE,
                            otherElement,
                            otherContext.getLocation(otherElement),
                            message);
                }
            }
        }

        // Also check arrays that appear in non-default locales but not in default
        for (Map.Entry<String, java.util.List<int[]>> entry : mOtherCounts.entrySet()) {
            String name = entry.getKey();
            if (mDefaultCounts.containsKey(name)) {
                continue; // already handled above
            }

            java.util.List<int[]> counts = entry.getValue();
            java.util.List<XmlContext> contexts = mOtherContexts.get(name);
            java.util.List<Element> elements = mOtherElements.get(name);

            if (counts.size() < 2) {
                continue;
            }

            // Check consistency among non-default locales
            int firstCount = counts.get(0)[0];
            XmlContext firstContext = contexts.get(0);
            String firstFolder = firstContext.file.getParentFile().getName();

            for (int i = 1; i < counts.size(); i++) {
                int otherCount = counts.get(i)[0];
                if (otherCount != firstCount) {
                    XmlContext otherContext = contexts.get(i);
                    Element otherElement = elements.get(i);
                    String otherFolder = otherContext.file.getParentFile().getName();

                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            name,
                            otherCount,
                            otherFolder,
                            firstCount,
                            firstFolder);

                    otherContext.report(
                            ISSUE,
                            otherElement,
                            otherContext.getLocation(otherElement),
                            message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = countItems(element);
        String folderName = context.file.getParentFile().getName();
        boolean isDefault = folderName.equals("values");

        if (isDefault) {
            // Store default locale info (last one wins if duplicates, but typically one per file)
            if (!mDefaultCounts.containsKey(name)) {
                mDefaultCounts.put(name, count);
                mDefaultContexts.put(name, context);
                mDefaultElements.put(name, element);
            }
        } else {
            java.util.List<int[]> countsList = mOtherCounts.get(name);
            if (countsList == null) {
                countsList = new java.util.ArrayList<>();
                mOtherCounts.put(name, countsList);
            }
            countsList.add(new int[]{count});

            java.util.List<XmlContext> contextsList = mOtherContexts.get(name);
            if (contextsList == null) {
                contextsList = new java.util.ArrayList<>();
                mOtherContexts.put(name, contextsList);
            }
            contextsList.add(context);

            java.util.List<Element> elementsList = mOtherElements.get(name);
            if (elementsList == null) {
                elementsList = new java.util.ArrayList<>();
                mOtherElements.put(name, elementsList);
            }
            elementsList.add(element);
        }
    }

    private int countItems(@NonNull Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }
}