package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceRepository;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends Detector implements XmlContext {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIdsInIncludedLayouts",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateIdDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE)
            )
    );

    private final Map<String, Set<Location>> idLocations = new HashMap<>();

    @Override
    public void beforeCheckFile(@NonNull Context context, @NonNull String file) {
        idLocations.clear();
    }

    @Nullable
    @Override
    public Set<String> getApplicableElements() {
        return new HashSet<>(Set.of(SdkConstants.TAG_INCLUDE, SdkConstants.TAG_LAYOUT));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String id = getId(element);
        if (id != null && !id.isEmpty()) {
            Location location = getLocation(context, element);
            Set<Location> locations = idLocations.get(id);
            if (locations == null) {
                locations = new HashSet<>();
                idLocations.put(id, locations);
            }
            locations.add(location);

            if (locations.size() > 1) {
                for (Location existing : locations) {
                    context.report(ISSUE, element, location,
                            "Duplicate ID found: %s", id);
                }
            }
        }

        String includeLayout = getIncludeLayout(element);
        if (includeLayout != null && !includeLayout.isEmpty()) {
            checkIncludedLayout(context, includeLayout);
        }
    }

    private void checkIncludedLayout(@NonNull XmlContext context, @NonNull String layoutName) {
        ResourceRepository repository = context.getResources();
        Element includedElement = repository.getLayout(layoutName + ".xml");
        if (includedElement != null) {
            visitDocument(context, includedElement);
        }
    }

    private Location getLocation(Context context, Element element) {
        Attr idAttr = getAttribute(element, "android:id");
        return Location.create(context.getProject(), element, idAttr);
    }

    @Nullable
    private String getId(Element element) {
        Attr attr = getAttribute(element, "android:id");
        if (attr != null && attr.getValue() != null) {
            return attr.getValue();
        }
        return null;
    }

    @Nullable
    private String getIncludeLayout(Element element) {
        Attr attr = getAttribute(element, SdkConstants.ATTR_LAYOUT);
        if (attr != null && attr.getValue() != null) {
            return attr.getValue().replace("@layout/", "");
        }
        return null;
    }

    @Nullable
    private Attr getAttribute(@NonNull Element element, @NonNull String attributeName) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attr = (Attr) element.getAttributes().item(i);
            if (attributeName.equals(attr.getName())) {
                return attr;
            }
        }
        return null;
    }
}