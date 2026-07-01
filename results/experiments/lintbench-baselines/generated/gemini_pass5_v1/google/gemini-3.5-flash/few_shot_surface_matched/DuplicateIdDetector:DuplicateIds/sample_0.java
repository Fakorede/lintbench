package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateIdDetector.class,
                            Scope.LAYOUT_RESOURCE_FILES));

    private final Set<String> mIds = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(com.android.SdkConstants.ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIds.clear();
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String id = attribute.getValue();
        if (id != null && !id.isEmpty()) {
            String cleanId = id;
            if (id.startsWith(com.android.SdkConstants.NEW_ID_PREFIX)) {
                cleanId = id.substring(com.android.SdkConstants.NEW_ID_PREFIX.length());
            } else if (id.startsWith(com.android.SdkConstants.ID_PREFIX)) {
                cleanId = id.substring(com.android.SdkConstants.ID_PREFIX.length());
            }
            if (mIds.contains(cleanId)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Duplicate id `" + id + "`, already defined earlier in this layout");
            } else {
                mIds.add(cleanId);
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
}