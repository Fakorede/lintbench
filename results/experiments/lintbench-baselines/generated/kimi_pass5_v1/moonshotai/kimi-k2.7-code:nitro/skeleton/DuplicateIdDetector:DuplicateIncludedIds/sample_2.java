package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
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
                    "Checks for id definitions that are duplicated across layouts that are "
                            + "combined via `<include>` tags. Because `findViewById()` searches the "
                            + "entire view hierarchy at runtime, a duplicate id in an included "
                            + "layout can cause the wrong view to be returned. Ids only need to "
                            + "be unique within a chain of included layouts.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, Id>> mIds = new HashMap<>();
    private final Map<String, List<Include>> mIncludes = new HashMap<>();
    private String mCurrentLayout;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentLayout = stripExtension(context.file.getName());
        mIds.put(mCurrentLayout, new HashMap<String, Id>());
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIds.clear();
        mIncludes.clear();
        mCurrentLayout = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Set<String> reported = new HashSet<>();
        for (String layout : mIncludes.keySet()) {
            Set<String> seen = new HashSet<>();
            Map<String, String> firstLayout = new HashMap<>();
            Set<String> visiting = new HashSet<>();
            check(context, layout, null, seen, firstLayout, visiting, reported);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mCurrentLayout == null || !"include".equals(element.getTagName())) {
            return;
        }
        String layoutRef = element.getAttribute("layout");
        if (layoutRef == null || layoutRef.isEmpty()) {
            return;
        }
        String target = getIncludedLayoutName(layoutRef);
        if (target == null) {
            return;
        }
        Location.Handle handle = context.getLocationHandle(element);
        List<Include> includes = mIncludes.get(mCurrentLayout);
        if (includes == null) {
            includes = new ArrayList<>();
            mIncludes.put(mCurrentLayout, includes);
        }
        includes.add(new Include(mCurrentLayout, target, handle));
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mCurrentLayout == null) {
            return;
        }
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@+id/")) {
            return;
        }
        String name = value.substring("@+id/".length());
        if (name.isEmpty()) {
            return;
        }
        Map<String, Id> ids = mIds.get(mCurrentLayout);
        if (ids == null) {
            return;
        }
        if (!ids.containsKey(name)) {
            ids.put(name, new Id(name, context.getLocationHandle(attribute)));
        }
    }

    private void check(
            @NonNull Context context,
            @NonNull String layout,
            Include incoming,
            @NonNull Set<String> seen,
            @NonNull Map<String, String> firstLayout,
            @NonNull Set<String> visiting,
            @NonNull Set<String> reported) {
        if (!visiting.add(layout)) {
            return;
        }
        Map<String, Id> ids = mIds.get(layout);
        if (ids != null) {
            for (Id id : ids.values()) {
                if (seen.contains(id.name)) {
                    String previous = firstLayout.get(id.name);
                    if (previous != null) {
                        String key = makeKey(id.name, previous, layout);
                        if (reported.add(key)) {
                            Location location =
                                    incoming != null ? incoming.handle.resolve() : id.handle.resolve();
                            String message =
                                    "The id `@+id/"
                                            + id.name
                                            + "` is already defined in `"
                                            + previous
                                            + ".xml`; including `"
                                            + layout
                                            + ".xml` can cause `findViewById()` to return an "
                                            + "unexpected view";
                            context.report(ISSUE, location, message);
                        }
                    }
                } else {
                    seen.add(id.name);
                    firstLayout.put(id.name, layout);
                }
            }
        }
        List<Include> includes = mIncludes.get(layout);
        if (includes != null) {
            for (Include inc : includes) {
                check(context, inc.to, inc, seen, firstLayout, visiting, reported);
            }
        }
        visiting.remove(layout);
    }

    private static String makeKey(@NonNull String id, @NonNull String layout1, @NonNull String layout2) {
        String first = layout1;
        String second = layout2;
        if (first.compareTo(second) > 0) {
            String tmp = first;
            first = second;
            second = tmp;
        }
        return id + ":" + first + ":" + second;
    }

    private static String getIncludedLayoutName(@NonNull String value) {
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return null;
        }
        return value.substring(slash + 1);
    }

    private static String stripExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static class Id {
        @NonNull final String name;
        @NonNull final Location.Handle handle;

        Id(@NonNull String name, @NonNull Location.Handle handle) {
            this.name = name;
            this.handle = handle;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class Include implements Comparable<Include> {
        @NonNull final String from;
        @NonNull final String to;
        @NonNull final Location.Handle handle;

        Include(@NonNull String from, @NonNull String to, @NonNull Location.Handle handle) {
            this.from = from;
            this.to = to;
            this.handle = handle;
        }

        @Override
        public String toString() {
            return from + " -> " + to;
        }

        @Override
        public int compareTo(@NonNull Include other) {
            int result = from.compareTo(other.from);
            if (result != 0) {
                return result;
            }
            result = to.compareTo(other.to);
            if (result != 0) {
                return result;
            }
            return Integer.compare(handle.hashCode(), other.handle.hashCode());
        }
    }
}