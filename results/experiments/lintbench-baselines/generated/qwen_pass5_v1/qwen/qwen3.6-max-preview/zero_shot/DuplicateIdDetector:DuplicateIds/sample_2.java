package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "DuplicateIds",
        "Duplicate ids within a single layout",
        "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, Attr> seenIds;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void beforeCheckFile(Context context) {
        seenIds = new HashMap<>();
    }

    @Override
    public void visitAttribute(Context context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String id = value;
        int slash = value.lastIndexOf('/');
        if (slash != -1) {
            id = value.substring(slash + 1);
        }

        Attr first = seenIds.get(id);
        if (first != null) {
            Location location = context.getLocation(attribute);
            Location secondary = context.getLocation(first);
            location.setSecondary(secondary);
            context.report(ISSUE, location,
                "Duplicate id `" + id + "`, already defined earlier in this layout");
        } else {
            seenIds.put(id, attribute);
        }
    }
}