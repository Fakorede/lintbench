package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DuplicateIdDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Set<String> mIds;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashSet<>();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String id = attribute.getValue();
        if (id != null && !id.isEmpty()) {
            String normalizedId = id;
            if (id.startsWith("@+id/")) {
                normalizedId = id.substring(5);
            } else if (id.startsWith("@id/")) {
                normalizedId = id.substring(4);
            }

            if (!mIds.add(normalizedId)) {
                context.report(ISSUE, context.getLocation(attribute),
                        String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
            }
        }
    }
}