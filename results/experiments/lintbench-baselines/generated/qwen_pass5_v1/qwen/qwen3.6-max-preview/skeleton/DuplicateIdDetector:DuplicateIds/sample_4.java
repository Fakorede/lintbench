package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
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

    private Map<String, Attr> mIds;

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
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide state needed
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No project-wide state needed
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not needed; detection is attribute-driven
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || !value.startsWith("@")) {
            return;
        }
        if (value.startsWith("@android:") || "@null".equals(value)) {
            return;
        }

        int slash = value.indexOf('/');
        String idName = slash != -1 ? value.substring(slash + 1) : value;

        Attr existing = mIds.get(idName);
        if (existing != null) {
            context.report(
                    ISSUE,
                    context.getLocation(attribute),
                    "Duplicate id `%1$s`, already defined earlier in this layout",
                    idName);
        } else {
            mIds.put(idName, attribute);
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(Object o) {
        return 0;
    }
}