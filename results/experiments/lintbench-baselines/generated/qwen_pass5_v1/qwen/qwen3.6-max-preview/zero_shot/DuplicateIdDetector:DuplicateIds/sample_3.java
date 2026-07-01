package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.SdkConstants;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Attr> mIds = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

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
        mIds.clear();
        mReported.clear();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String id = value;
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            id = id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            id = id.substring(SdkConstants.ID_PREFIX.length());
        } else {
            return;
        }

        if (mReported.contains(id)) {
            return;
        }

        Attr first = mIds.get(id);
        if (first != null) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Duplicate id `" + id + "`, already defined earlier in this layout");
            mReported.add(id);
        } else {
            mIds.put(id, attribute);
        }
    }
}