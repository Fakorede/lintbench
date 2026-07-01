package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with `include` tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Map from layout name to the set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout name to list of layouts included by that layout */
    private Map<String, List<String>> mIncludes;

    /** Current file being processed - set of ids */
    private Set<String> mIds;

    /** Current file being processed - list of included layouts */
    private List<String> mCurrentIncludes;

    /** Current file name (without extension) */
    private String mCurrentFile;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            mCurrentFile = name.substring(0, dot);
        } else {
            mCurrentFile = name;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mFileToIds == null) {
            mFileToIds = new HashMap<>();
        }
        if (mIncludes == null) {
            mIncludes = new HashMap<>();
        }
        mFileToIds.put(mCurrentFile, mIds);
        if (!mCurrentIncludes.isEmpty()) {
            mIncludes.put(mCurrentFile, mCurrentIncludes);
        }
        mIds = null;
        mCurrentIncludes = null;
        mCurrentFile = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // For each layout that includes other layouts, check for duplicate ids
        // across the include chain
        if (mIncludes == null || mIncludes.isEmpty()) {
            return;
        }

        // For each file that has includes, resolve the full set of ids
        // and check for duplicates
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids from this layout and all included layouts
            // Track which layout each id comes from
            Map<String, String> idToLayout = new HashMap<>();
            Set<String> visited = new HashSet<>();

            // Add ids from the root layout
            Set<String> rootIds = mFileToIds.get(layout);
            if (rootIds != null) {
                for (String id : rootIds) {
                    idToLayout.put(id, layout);
                }
            }

            // Now check included layouts for duplicates
            checkIncludedLayouts(context, layout, includes, idToLayout, visited);
        }
    }

    private void checkIncludedLayouts(
            @NonNull Context context,
            @NonNull String rootLayout,
            @NonNull List<String> includes,
            @NonNull Map<String, String> idToLayout,
            @NonNull Set<String> visited) {
        for (String included : includes) {
            if (visited.contains(included)) {
                continue;
            }
            visited.add(included);

            Set<String> includedIds = mFileToIds.get(included);
            if (includedIds != null) {
                for (String id : includedIds) {
                    if (idToLayout.containsKey(id)) {
                        // Duplicate id found across included layouts
                        String firstLayout = idToLayout.get(id);
                        context.report(
                                ISSUE,
                                Location.create(context.file),
                                String.format(
                                        "Duplicate id `@+id/%1$s` in layout `%2$s` and layout `%3$s` "
                                                + "(included from layout `%4$s`)",
                                        id,
                                        firstLayout,
                                        included,
                                        rootLayout));
                    } else {
                        idToLayout.put(id, included);
                    }
                }
            }

            // Recursively check layouts included by this included layout
            List<String> nestedIncludes = mIncludes.get(included);
            if (nestedIncludes != null) {
                checkIncludedLayouts(context, rootLayout, nestedIncludes, idToLayout, visited);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> elements
        if (VIEW_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                if (mCurrentIncludes != null) {
                    mCurrentIncludes.add(includedLayout);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Handle android:id attributes
        if (ANDROID_URI.equals(attribute.getNamespaceURI())
                && ATTR_ID.equals(attribute.getLocalName())) {
            String id = attribute.getValue();
            if (id != null) {
                // Strip @+id/ or @id/ prefix
                if (id.startsWith("@+id/")) {
                    id = id.substring("@+id/".length());
                } else if (id.startsWith("@id/")) {
                    id = id.substring("@id/".length());
                }
                if (mIds != null && !id.isEmpty()) {
                    mIds.add(id);
                }
            }
        }
    }

    /**
     * Returns a string representation of the given layout and its ids.
     *
     * @param layout the layout name
     * @return a string describing the layout
     */
    public String toString(String layout) {
        if (mFileToIds != null) {
            Set<String> ids = mFileToIds.get(layout);
            if (ids != null) {
                List<String> sorted = new ArrayList<>(ids);
                Collections.sort(sorted);
                return layout + ": " + sorted.toString();
            }
        }
        return layout + ": []";
    }

    /**
     * Compares two layout names alphabetically.
     *
     * @param layout1 the first layout name
     * @param layout2 the second layout name
     * @return a negative integer, zero, or a positive integer as the first
     *         argument is less than, equal to, or greater than the second
     */
    public int compareTo(String layout1, String layout2) {
        if (layout1 == null && layout2 == null) {
            return 0;
        } else if (layout1 == null) {
            return -1;
        } else if (layout2 == null) {
            return 1;
        }
        return layout1.compareTo(layout2);
    }
}