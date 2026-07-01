package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateIdDetector.class, Scope.LAYOUT_RESOURCE_FILE_SCOPE));

    private java.util.Set<String> mIds;

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(com.android.SdkConstants.ATTR_ID);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return com.android.tools.lint.detector.api.XmlScanner.ALL;
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull XmlContext context) {
        mIds = new java.util.HashSet<>();
    }

    @Override
    public void afterCheckFile(@com.android.annotations.NonNull XmlContext context) {
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        // No-op
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        String id = attribute.getValue();
        if (id != null && !id.isEmpty()) {
            String cleanId = id;
            if (id.startsWith(com.android.SdkConstants.NEW_ID_PREFIX)) {
                cleanId = id.substring(com.android.SdkConstants.NEW_ID_PREFIX.length());
            } else if (id.startsWith(com.android.SdkConstants.ID_PREFIX)) {
                cleanId = id.substring(com.android.SdkConstants.ID_PREFIX.length());
            }
            if (mIds != null) {
                if (mIds.contains(cleanId)) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Duplicate id `" + id + "`, already defined earlier in this layout");
                } else {
                    mIds.add(cleanId);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(@com.android.annotations.NonNull Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
}