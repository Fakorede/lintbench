package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.VIEW_INCLUDE;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the id's need to be unique "
                            + "within any chain of included layouts, or `Activity#findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout resource name to the set of ids defined in that layout */
    private Map<String, Set<String>> mFileToIds;

    /** Map from layout resource name to the list of layouts it includes */
    private Map<String, List<String>> mIncludes;

    /** Map from layout resource name to file, for error reporting */
    private Map<String, File> mFileMap;

    /** Current file's id set being built */
    private Set<String> mCurrentIds;

    /** Current file's include list being built */
    private List<String> mCurrentIncludes;

    /** Current layout name */
    private String mCurrentLayout;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ID, ATTR_LAYOUT);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mFileMap = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        File file = context.file;
        String name = getLayoutName(file);
        mCurrentLayout = name;
        mFileMap.put(name, file);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mCurrentLayout != null) {
            mFileToIds.put(mCurrentLayout, mCurrentIds);
            if (!mCurrentIncludes.isEmpty()) {
                mIncludes.put(mCurrentLayout, mCurrentIncludes);
            }
        }
        mCurrentIds = null;
        mCurrentIncludes = null;
        mCurrentLayout = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now that we have all the data, check for duplicate ids across includes
        if (mIncludes.isEmpty()) {
            return;
        }

        // For each layout that has includes, compute the full set of ids reachable
        // and check for duplicates
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids from this layout and all transitively included layouts
            Map<String, List<String>> idToLayouts = new HashMap<>();

            // Add ids from current layout
            Set<String> currentIds = mFileToIds.get(layout);
            if (currentIds != null) {
                for (String id : currentIds) {
                    List<String> list = new ArrayList<>();
                    list.add(layout);
                    idToLayouts.put(id, list);
                }
            }

            // Add ids from included layouts (transitively)
            Set<String> visited = new HashSet<>();
            visited.add(layout);
            collectIds(includes, idToLayouts, visited);

            // Report any duplicates
            for (Map.Entry<String, List<String>> idEntry : idToLayouts.entrySet()) {
                List<String> layouts = idEntry.getValue();
                if (layouts.size() > 1) {
                    String id = idEntry.getKey();
                    File file = mFileMap.get(layout);
                    if (file != null) {
                        Location location = Location.create(file);
                        StringBuilder sb = new StringBuilder();
                        sb.append("Duplicate id `");
                        sb.append(id);
                        sb.append("` across layouts: [");
                        for (int i = 0; i < layouts.size(); i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(layouts.get(i));
                        }
                        sb.append("]");
                        context.report(ISSUE, location, sb.toString());
                    }
                }
            }
        }
    }

    private void collectIds(
            @NonNull List<String> includes,
            @NonNull Map<String, List<String>> idToLayouts,
            @NonNull Set<String> visited) {
        for (String included : includes) {
            if (visited.contains(included)) {
                continue;
            }
            visited.add(included);

            Set<String> ids = mFileToIds.get(included);
            if (ids != null) {
                for (String id : ids) {
                    List<String> list = idToLayouts.get(id);
                    if (list == null) {
                        list = new ArrayList<>();
                        idToLayouts.put(id, list);
                    }
                    if (!list.contains(included)) {
                        list.add(included);
                    }
                }
            }

            List<String> subIncludes = mIncludes.get(included);
            if (subIncludes != null) {
                collectIds(subIncludes, idToLayouts, visited);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <include> elements
        String layout = element.getAttribute("layout");
        if (layout != null && !layout.isEmpty()) {
            String includedLayout = stripLayoutPrefix(layout);
            if (includedLayout != null && mCurrentIncludes != null) {
                mCurrentIncludes.add(includedLayout);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (ATTR_ID.equals(name)) {
            String value = attribute.getValue();
            if (value != null && !value.isEmpty() && mCurrentIds != null) {
                mCurrentIds.add(value);
            }
        } else if (ATTR_LAYOUT.equals(name)) {
            // This handles layout= on <include> tags
            String value = attribute.getValue();
            String includedLayout = stripLayoutPrefix(value);
            if (includedLayout != null && mCurrentIncludes != null) {
                if (!mCurrentIncludes.contains(includedLayout)) {
                    mCurrentIncludes.add(includedLayout);
                }
            }
        }
    }

    @Nullable
    private static String stripLayoutPrefix(@Nullable String layout) {
        if (layout == null) {
            return null;
        }
        if (layout.startsWith("@layout/")) {
            return layout.substring("@layout/".length());
        } else if (layout.startsWith("@android:layout/")) {
            return null; // framework layouts, skip
        }
        return null;
    }

    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(@NonNull DuplicateIdDetector other) {
        return toString().compareTo(other.toString());
    }
}