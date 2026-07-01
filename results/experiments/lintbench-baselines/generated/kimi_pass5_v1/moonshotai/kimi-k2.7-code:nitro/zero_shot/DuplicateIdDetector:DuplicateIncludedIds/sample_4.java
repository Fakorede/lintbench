package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate IDs across included layouts",
            "It is okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the ids need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.LAYOUT,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES)
    );

    private final Map<String, Map<String, List<Location>>> mLayoutToIds = new HashMap<>();
    private final Map<String, Set<String>> mLayoutToIncludes = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(SdkConstants.ATTR_ID, SdkConstants.ATTR_LAYOUT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        String value = attribute.getValue();
        if (SdkConstants.ATTR_ID.equals(name)) {
            String idName = getIdName(value);
            if (idName != null && !idName.isEmpty()) {
                String layout = getBaseLayoutName(context.file);
                mLayoutToIds
                        .computeIfAbsent(layout, k -> new HashMap<>())
                        .computeIfAbsent(idName, k -> new ArrayList<>())
                        .add(context.getValueLocation(attribute));
            }
        } else if (SdkConstants.ATTR_LAYOUT.equals(name)) {
            String target = getLayoutReferenceName(value);
            if (target != null
                    && SdkConstants.TAG_INCLUDE.equals(attribute.getOwnerElement().getTagName())) {
                String layout = getBaseLayoutName(context.file);
                mLayoutToIncludes
                        .computeIfAbsent(layout, k -> new HashSet<>())
                        .add(target);
            }
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLayoutToIds.clear();
        mLayoutToIncludes.clear();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        Set<String> roots = new HashSet<>();
        roots.addAll(mLayoutToIds.keySet());
        roots.addAll(mLayoutToIncludes.keySet());

        for (String root : roots) {
            Set<String> closure = computeClosure(root);

            Map<String, Set<String>> idToFiles = new HashMap<>();
            for (String layout : closure) {
                Map<String, List<Location>> ids = mLayoutToIds.get(layout);
                if (ids == null) {
                    continue;
                }
                for (Map.Entry<String, List<Location>> entry : ids.entrySet()) {
                    idToFiles
                            .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                            .add(layout);
                }
            }

            for (Map.Entry<String, Set<String>> entry : idToFiles.entrySet()) {
                if (entry.getValue().size() > 1) {
                    String idName = entry.getKey();
                    for (String layout : entry.getValue()) {
                        Map<String, List<Location>> ids = mLayoutToIds.get(layout);
                        if (ids == null) {
                            continue;
                        }
                        List<Location> locations = ids.get(idName);
                        if (locations == null) {
                            continue;
                        }
                        for (Location location : locations) {
                            String message = String.format(
                                    "Duplicate id \"%1$s\" defined in multiple layouts that can be "
                                            + "combined with <include>; Activity#findViewById() may "
                                            + "return an unexpected view",
                                    idName
                            );
                            context.report(ISSUE, location, message);
                        }
                    }
                }
            }
        }
    }

    private Set<String> computeClosure(String root) {
        Set<String> closure = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!closure.add(current)) {
                continue;
            }
            Set<String> includes = mLayoutToIncludes.get(current);
            if (includes == null) {
                continue;
            }
            for (String next : includes) {
                if (!closure.contains(next)) {
                    stack.push(next);
                }
            }
        }

        return closure;
    }

    private static String getIdName(String value) {
        if (value == null) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return null;
        }
        int at = value.indexOf('@');
        if (at == -1) {
            return null;
        }
        String prefix = value.substring(0, slash);
        if (prefix.contains("id")) {
            return value.substring(slash + 1);
        }
        return null;
    }

    private static String getLayoutReferenceName(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("@layout/")) {
            return value.substring("@layout/".length());
        }
        return null;
    }

    private static String getBaseLayoutName(File file) {
        String name = file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
}