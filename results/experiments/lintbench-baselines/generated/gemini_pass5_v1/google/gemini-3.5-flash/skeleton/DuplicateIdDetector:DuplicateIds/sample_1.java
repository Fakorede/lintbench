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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

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

    private Map<String, Attr> mSeenIds;

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
        mSeenIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mSeenIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if ("id".equals(name) && "http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            String value = attribute.getValue();
            String id = value;
            if (value.startsWith("@+id/")) {
                id = value.substring(5);
            } else if (value.startsWith("@id/")) {
                id = value.substring(4);
            }
            if (id.isEmpty()) {
                return;
            }
            if (mSeenIds != null) {
                if (mSeenIds.containsKey(id)) {
                    Attr original = mSeenIds.get(id);
                    Location location = context.getLocation(attribute);
                    Location secondary = context.getLocation(original);
                    secondary.setMessage("Original occurrence here");
                    location.setSecondary(secondary);
                    context.report(
                            ISSUE,
                            attribute,
                            location,
                            String.format("Duplicate id `%s`, already defined earlier in this layout", value));
                } else {
                    mSeenIds.put(id, attribute);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(DuplicateIdDetector other) {
        return 0;
    }
}