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
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String NEW_ID_PREFIX = "@+id/";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private Map<String, List<Element>> mIds;

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
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIds == null || mIds.isEmpty()) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, List<Element>> entry : mIds.entrySet()) {
            List<Element> elements = entry.getValue();
            if (elements.size() <= 1) {
                continue;
            }

            String id = entry.getKey();
            Location location = null;
            for (Element element : elements) {
                Location elementLocation = xmlContext.getLocation(element);
                if (location == null) {
                    location = elementLocation;
                } else {
                    location = location.withSecondary(elementLocation, "Also defines id");
                }
            }

            xmlContext.report(
                    ISSUE,
                    elements.get(0),
                    location,
                    String.format("Duplicate id %1$s%2$s", NEW_ID_PREFIX, id));
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide state required.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No project-wide state required.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Attribute visits are sufficient for duplicate id detection.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith(NEW_ID_PREFIX)) {
            return;
        }

        String id = value.substring(NEW_ID_PREFIX.length());
        if (id.isEmpty()) {
            return;
        }

        List<Element> elements = mIds.get(id);
        if (elements == null) {
            elements = new ArrayList<>();
            mIds.put(id, elements);
        }
        elements.add(attribute.getOwnerElement());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + (mIds == null ? "" : ": " + mIds.toString());
    }

    public int compareTo(DuplicateIdDetector other) {
        return 0;
    }
}