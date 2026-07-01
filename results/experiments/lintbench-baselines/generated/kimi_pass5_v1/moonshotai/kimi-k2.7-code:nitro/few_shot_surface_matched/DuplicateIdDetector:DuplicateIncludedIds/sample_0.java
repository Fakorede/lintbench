package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.TAG_INCLUDE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue DUPLICATE_INCLUDED_IDS =
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
                    new Implementation(
                            DuplicateIdDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.ALL_RESOURCE_FILES));

    private Map<String, Set<String>> mIds;
    private Map<String, List<IncludeEdge>> mIncludes;

    private Set<String> mCurrentIds;
    private List<IncludeEdge> mCurrentIncludes;
    private String mCurrentLayoutName;

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
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        mCurrentLayoutName = LintUtils.getLayoutName(context.file);
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!mCurrentIds.isEmpty()) {
            if (mIds == null) {
                mIds = new HashMap<>();
            }
            mIds.put(mCurrentLayoutName, mCurrentIds);
        }
        if (!mCurrentIncludes.isEmpty()) {
            if (mIncludes == null) {
                mIncludes = new HashMap<>();
            }
            mIncludes.put(mCurrentLayoutName, mCurrentIncludes);
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes == null || mIncludes.isEmpty() || mIds == null) {
            return;
        }

        for (String root : new HashSet<>(mIncludes.keySet())) {
            checkRoot(context, root);
        }
    }

    private void checkRoot(@NonNull Context context, @NonNull String root) {
        Map<String, List<Occurrence>> idMap = new HashMap<>();

        Set<String> rootIds = mIds.get(root);
        if (rootIds != null) {
            for (String id : rootIds) {
                idMap.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new Occurrence(root, null));
            }
        }

        List<IncludeEdge> outgoing = mIncludes.get(root);
        if (outgoing == null) {
            return;
        }

        Set<String> visited = new HashSet<>();
        visited.add(root);
        Deque<IncludeEdge> stack = new ArrayDeque<>(outgoing);

        while (!stack.isEmpty()) {
            IncludeEdge edge = stack.pop();
            String target = edge.target;
            Location includeLocation = edge.location;

            Set<String> targetIds = mIds.get(target);
            if (targetIds != null) {
                for (String id : targetIds) {
                    idMap.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new Occurrence(target, includeLocation));
                }
            }

            if (visited.add(target)) {
                List<IncludeEdge> next = mIncludes.get(target);
                if (next != null) {
                    stack.addAll(next);
                }
            }
        }

        for (Map.Entry<String, List<Occurrence>> entry : idMap.entrySet()) {
            String id = entry.getKey();
            List<Occurrence> occurrences = entry.getValue();

            Set<String> layouts = new HashSet<>();
            Map<String, List<Location>> includeLocationsByTarget = new HashMap<>();
            for (Occurrence occurrence : occurrences) {
                layouts.add(occurrence.layout);
                if (occurrence.includeLocation != null) {
                    includeLocationsByTarget
                            .computeIfAbsent(occurrence.layout, k -> new ArrayList<>())
                            .add(occurrence.includeLocation);
                }
            }

            if (layouts.size() <= 1) {
                continue;
            }

            for (Map.Entry<String, List<Location>> targetEntry :
                    includeLocationsByTarget.entrySet()) {
                String target = targetEntry.getKey();
                Set<String> others = new HashSet<>(layouts);
                others.remove(target);
                if (others.isEmpty()) {
                    continue;
                }

                String message = buildMessage(id, target, others);
                for (Location location : targetEntry.getValue()) {
                    context.report(DUPLICATE_INCLUDED_IDS, location, message);
                }
            }
        }
    }

    private String buildMessage(
            @NonNull String id, @NonNull String target, @NonNull Set<String> others) {
        StringBuilder sb = new StringBuilder();
        sb.append("Duplicate id `@+id/")
                .append(id)
                .append("` is defined in `layout/")
                .append(target)
                .append("` and also in ");
        boolean first = true;
        for (String other : others) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("`layout/").append(other).append("`");
            first = false;
        }
        return sb.toString();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layout = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT);
        if (layout == null || layout.isEmpty()) {
            return;
        }
        int slash = layout.lastIndexOf('/');
        if (slash == -1 || slash == layout.length() - 1) {
            return;
        }
        String target = layout.substring(slash + 1);
        mCurrentIncludes.add(new IncludeEdge(target, context.getLocation(element)));
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String id = getIdName(attribute.getValue());
        if (id != null) {
            mCurrentIds.add(id);
        }
    }

    private static String getIdName(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            return value.substring(value.lastIndexOf('/') + 1);
        }
        if (value.startsWith("@+android:id/") || value.startsWith("@android:id/")) {
            return "android:" + value.substring(value.lastIndexOf('/') + 1);
        }
        return null;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(@NonNull Detector other) {
        return super.compareTo(other);
    }

    private static class IncludeEdge {
        final String target;
        final Location location;

        IncludeEdge(String target, Location location) {
            this.target = target;
            this.location = location;
        }
    }

    private static class Occurrence {
        final String layout;
        final Location includeLocation;

        Occurrence(String layout, Location includeLocation) {
            this.layout = layout;
            this.includeLocation = includeLocation;
        }
    }
}