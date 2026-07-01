package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.TAG_INCLUDE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the ids need to be "
                            + "unique within any chain of included layouts, or "
                            + "`Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES));

    private Map<String, LayoutRecord> mLayouts;
    private LayoutRecord mCurrentLayout;

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
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayouts = new HashMap<>();
        mCurrentLayout = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        String name = getBaseName(context.getFile().getName());
        mCurrentLayout = new LayoutRecord(name);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mCurrentLayout == null) {
            return;
        }

        String layout = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT);
        if (layout == null || layout.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(layout);
        if (url != null && url.type == ResourceType.LAYOUT && !url.framework) {
            mCurrentLayout.includes.add(
                    new IncludeOccurrence(url.name, context.getLocation(element)));
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mCurrentLayout == null) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && url.type == ResourceType.ID && !url.framework) {
            mCurrentLayout.ids.add(
                    new IdOccurrence(
                            url.name, mCurrentLayout.name, context.getLocation(attribute)));
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mCurrentLayout == null) {
            return;
        }

        LayoutRecord existing = mLayouts.get(mCurrentLayout.name);
        if (existing == null) {
            mLayouts.put(mCurrentLayout.name, mCurrentLayout);
        } else {
            existing.ids.addAll(mCurrentLayout.ids);
            existing.includes.addAll(mCurrentLayout.includes);
        }

        mCurrentLayout = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayouts == null || mLayouts.isEmpty()) {
            return;
        }

        for (LayoutRecord layout : mLayouts.values()) {
            for (IncludeOccurrence include : layout.includes) {
                LayoutRecord target = mLayouts.get(include.layoutName);
                if (target != null) {
                    target.referenced = true;
                }
            }
        }

        for (LayoutRecord layout : mLayouts.values()) {
            if (!layout.referenced) {
                checkDuplicates(
                        layout,
                        context,
                        new HashSet<String>(),
                        new HashMap<String, IdOccurrence>(),
                        new HashSet<String>());
            }
        }

        mLayouts = null;
        mCurrentLayout = null;
    }

    private void checkDuplicates(
            @NonNull LayoutRecord layout,
            @NonNull Context context,
            @NonNull Set<String> seenIds,
            @NonNull Map<String, IdOccurrence> firstOccurrence,
            @NonNull Set<String> visiting) {
        if (!visiting.add(layout.name)) {
            return;
        }

        for (IdOccurrence occurrence : layout.ids) {
            if (!seenIds.add(occurrence.id)) {
                IdOccurrence first = firstOccurrence.get(occurrence.id);
                String firstName = first != null ? first.layoutName : "unknown";
                String message =
                        "Duplicate id value: @+id/"
                                + occurrence.id
                                + " (already defined in "
                                + firstName
                                + ")";
                context.report(ISSUE, occurrence.location, message);
            } else {
                firstOccurrence.put(occurrence.id, occurrence);
            }
        }

        for (IncludeOccurrence include : layout.includes) {
            LayoutRecord target = mLayouts.get(include.layoutName);
            if (target != null) {
                checkDuplicates(target, context, seenIds, firstOccurrence, visiting);
            }
        }

        visiting.remove(layout.name);
    }

    @Override
    public int compareTo(@NonNull Detector other) {
        int delta = other.getPriority() - getPriority();
        if (delta != 0) {
            return delta;
        }
        return getClass().getName().compareTo(other.getClass().getName());
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    private static class LayoutRecord {
        final String name;
        final List<IdOccurrence> ids = new ArrayList<>();
        final List<IncludeOccurrence> includes = new ArrayList<>();
        boolean referenced;

        LayoutRecord(String name) {
            this.name = name;
        }
    }

    private static class IdOccurrence {
        final String id;
        final String layoutName;
        final Location location;

        IdOccurrence(String id, String layoutName, Location location) {
            this.id = id;
            this.layoutName = layoutName;
            this.location = location;
        }
    }

    private static class IncludeOccurrence {
        final String layoutName;
        final Location location;

        IncludeOccurrence(String layoutName, Location location) {
            this.layoutName = layoutName;
            this.location = location;
        }
    }
}