package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    private Set<String> mIds;

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
        mIds = new HashSet<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mIds = null;
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
        String namespace = attribute.getNamespaceURI();
        if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
            String value = attribute.getValue();
            String id = normalizeId(value);
            if (id != null && !id.isEmpty()) {
                if (mIds != null && mIds.contains(id)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Duplicate id `" + value + "`, already defined earlier in this layout");
                } else if (mIds != null) {
                    mIds.add(id);
                }
            }
        }
    }

    private String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        int index = id.indexOf('/');
        if (index != -1) {
            return id.substring(index + 1);
        }
        return id;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(Object o) {
        return 0;
    }
}