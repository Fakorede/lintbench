package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue DUPLICATE_INCLUDED_IDS =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate IDs in included layouts",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the ids need to be "
                            + "unique within any chain of included layouts, or "
                            + "`Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES));

    private static final String ANDROID_ID = "android:id";
    private static final String ANDROID_LAYOUT = "android:layout";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_INCLUDE = "include";

    private Map<String, List<LayoutData>> mLayoutMap;
    private LayoutData mCurrentLayout;

    private static class IdOccurrence {
        final String id;
        final Location location;

        IdOccurrence(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    private static class IncludeOccurrence {
        final String target;
        final Location location;
        final String explicitId;

        IncludeOccurrence(String target, Location location, String explicitId) {
            this.target = target;
            this.location = location;
            this.explicitId = explicitId;
        }
    }

    private static class LayoutData {
        final String name;
        final List<IdOccurrence> ids = new ArrayList<>();
        final List<IncludeOccurrence> includes = new ArrayList<>();
        String rootId;

        LayoutData(String name) {
            this.name = name;
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ANDROID_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mLayoutMap = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String name = dot == -1 ? fileName : fileName.substring(0, dot);
        mCurrentLayout = new LayoutData(name);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (mCurrentLayout == null) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String idName = extractIdName(value);
        if (idName == null || idName.isEmpty()) {
            return;
        }

        mCurrentLayout.ids.add(new IdOccurrence(idName, context.getLocation(attribute)));

        org.w3c.dom.Element owner = attribute.getOwnerElement();
        org.w3c.dom.Element root = context.document.getDocumentElement();
        if (owner == root) {
            mCurrentLayout.rootId = idName;
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (mCurrentLayout == null) {
            return;
        }

        String layoutValue = element.getAttributeNS(ANDROID_NS, "layout");
        if (layoutValue == null || layoutValue.isEmpty()) {
            return;
        }

        String target = extractLayoutName(layoutValue);
        if (target == null || target.isEmpty()) {
            return;
        }

        String explicitId = null;
        String idValue = element.getAttributeNS(ANDROID_NS, "id");
        if (idValue != null && !idValue.isEmpty()) {
            explicitId = extractIdName(idValue);
        }

        mCurrentLayout.includes.add(
                new IncludeOccurrence(target, context.getLocation(element), explicitId));
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mCurrentLayout != null) {
            mLayoutMap.computeIfAbsent(mCurrentLayout.name, k -> new ArrayList<>()).add(mCurrentLayout);
            mCurrentLayout = null;
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        Set<String> reported = new HashSet<>();
        for (List<LayoutData> layouts : mLayoutMap.values()) {
            for (LayoutData layout : layouts) {
                Set<String> startingIds = new HashSet<>();
                for (IdOccurrence occ : layout.ids) {
                    startingIds.add(occ.id);
                }
                checkIncludes(layout, startingIds, reported, new HashSet<>(), context);
            }
        }
    }

    private void checkIncludes(
            LayoutData layout,
            Set<String> ids,
            Set<String> reported,
            Set<LayoutData> visiting,
            Context context) {
        if (!visiting.add(layout)) {
            return;
        }

        for (IncludeOccurrence inc : layout.includes) {
            List<LayoutData> children = mLayoutMap.get(inc.target);
            if (children == null) {
                continue;
            }

            for (LayoutData child : children) {
                Set<String> childIds = new HashSet<>();
                for (IdOccurrence occ : child.ids) {
                    if (inc.explicitId != null && occ.id.equals(child.rootId)) {
                        continue;
                    }
                    childIds.add(occ.id);
                }

                for (String id : childIds) {
                    if (ids.contains(id)) {
                        String key = layout.name + "->" + child.name + ":" + id;
                        if (reported.add(key)) {
                            context.report(
                                    DUPLICATE_INCLUDED_IDS,
                                    inc.location,
                                    "Duplicate id `" + id + "` in included layout `"
                                            + child.name + "`");
                        }
                    }
                }

                Set<String> merged = new HashSet<>(ids);
                if (inc.explicitId != null) {
                    merged.add(inc.explicitId);
                }
                merged.addAll(childIds);

                checkIncludes(child, merged, reported, visiting, context);
            }
        }

        visiting.remove(layout);
    }

    private String extractIdName(String value) {
        if (value == null) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        int plus = name.indexOf('+');
        if (plus >= 0) {
            name = name.substring(plus + 1);
        }
        return name;
    }

    private String extractLayoutName(String value) {
        if (value == null) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(Detector other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }
}