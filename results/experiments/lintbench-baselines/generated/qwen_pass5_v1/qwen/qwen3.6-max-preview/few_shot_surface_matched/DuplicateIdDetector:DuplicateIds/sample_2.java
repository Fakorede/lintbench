package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
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
                    6,
                    Severity.FATAL,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Set<String> mIds;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIds = new HashSet<>();
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mIds != null) {
            mIds.clear();
            mIds = null;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide initialization required
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No project-wide cleanup required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Element visiting not required for this attribute-based check
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String id = value;
        if (id.startsWith("@+id/")) {
            id = id.substring(5);
        } else if (id.startsWith("@id/")) {
            id = id.substring(4);
        } else if (id.startsWith("@android:id/")) {
            return;
        }

        if (mIds != null) {
            if (mIds.contains(id)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Duplicate id `" + value + "`, already defined earlier in this layout");
            } else {
                mIds.add(id);
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(@NonNull Detector other) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
}