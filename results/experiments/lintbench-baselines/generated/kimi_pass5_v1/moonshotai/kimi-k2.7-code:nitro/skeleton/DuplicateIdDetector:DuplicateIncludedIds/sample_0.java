package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_LAYOUT = "layout";
    private static final String INCLUDE_TAG = "include";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the ids need to be "
                            + "unique within any chain of included layouts, or "
                            + "`Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<IdInfo>> mIdsByLayout;
    private Map<String, List<IncludeInfo>> mIncludesByLayout;

    private static class IdInfo {
        final String id;
        final Location location;

        IdInfo(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    private static class IncludeInfo {
        final String layout;
        final Location location;

        IncludeInfo(String layout, Location location) {
            this.layout = layout;
            this.location = location;
        }
    }

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
        return Collections.singletonList(INCLUDE_TAG);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIdsByLayout = new HashMap<>();
        mIncludesByLayout = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIdsByLayout.isEmpty() || mIncludesByLayout.isEmpty()) {
            cleanup();
            return;
        }

        Set<String> allLayouts = new HashSet<>();
        allLayouts.addAll(mIdsByLayout.keySet());
        allLayouts.addAll(mIncludesByLayout.keySet());

        Set<String> includedLayouts = new HashSet<>();
        for (List<IncludeInfo> includes : mIncludesByLayout.values()) {
            for (IncludeInfo include : includes) {
                includedLayouts.add(include.layout);
            }
        }

        Set<String> roots = new HashSet<>(allLayouts);
        roots.removeAll(includedLayouts);
        if (roots.isEmpty()) {
            roots.addAll(allLayouts);
        }

        for (String root : roots) {
            checkLayout(context, root, new HashSet<String>(), new HashMap<String, Location>());
        }

        cleanup();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!INCLUDE_TAG.equals(element.getLocalName())) {
            return;
        }
        Attr layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
        if (layoutAttr == null) {
            return;
        }
        String includedLayout = getLayoutReference(layoutAttr.getValue());
        if (includedLayout == null) {
            return;
        }
        String layout = getLayoutName(context);
        mIncludesByLayout.computeIfAbsent(layout, k -> new ArrayList<>())
                .add(new IncludeInfo(includedLayout, context.getLocation(layoutAttr)));
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (!ATTR_ID.equals(attribute.getLocalName())) {
            return;
        }
        String id = getIdName(attribute.getValue());
        if (id == null) {
            return;
        }
        String layout = getLayoutName(context);
        mIdsByLayout.computeIfAbsent(layout, k -> new ArrayList<>())
                .add(new IdInfo(id, context.getLocation(attribute)));
    }

    private void checkLayout(Context context, String layout, Set<String> path,
                             Map<String, Location> ids) {
        if (path.contains(layout)) {
            return;
        }
        path.add(layout);
        compareTo(context, layout, ids);
        List<IncludeInfo> includes = mIncludesByLayout.get(layout);
        if (includes != null) {
            for (IncludeInfo include : includes) {
                checkLayout(context, include.layout, path, ids);
            }
        }
        path.remove(layout);
    }

    public void compareTo(@NonNull Context context, @NonNull String layout,
                          @NonNull Map<String, Location> ids) {
        List<IdInfo> idList = mIdsByLayout.get(layout);
        if (idList == null) {
            return;
        }
        for (IdInfo info : idList) {
            Location first = ids.get(info.id);
            if (first != null) {
                reportDuplicate(context, info, first);
            } else {
                ids.put(info.id, info.location);
            }
        }
    }

    public void toString(@NonNull StringBuilder sb, @NonNull String layout) {
        sb.append(layout).append(": ");
        List<IdInfo> ids = mIdsByLayout.get(layout);
        if (ids != null) {
            for (IdInfo info : ids) {
                sb.append(info.id).append(' ');
            }
        }
        List<IncludeInfo> includes = mIncludesByLayout.get(layout);
        if (includes != null) {
            sb.append("includes: ");
            for (IncludeInfo info : includes) {
                sb.append(info.layout).append(' ');
            }
        }
    }

    private void reportDuplicate(Context context, IdInfo duplicate, Location first) {
        String message = String.format(
                "Duplicate id `%1$s` found in included layouts (first occurrence in %2$s)",
                duplicate.id,
                first.getFile().getName());
        context.report(ISSUE, duplicate.location, message);
    }

    private void cleanup() {
        mIdsByLayout = null;
        mIncludesByLayout = null;
    }

    private static String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String getIdName(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("@+id/")) {
            return value.substring("@+id/".length());
        }
        if (value.startsWith("@id/")) {
            return value.substring("@id/".length());
        }
        if (value.startsWith("@android:id/")) {
            return value.substring("@android:id/".length());
        }
        return null;
    }

    private static String getLayoutReference(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("@layout/")) {
            return value.substring("@layout/".length());
        }
        if (value.startsWith("@android:layout/")) {
            return value.substring("@android:layout/".length());
        }
        return null;
    }
}