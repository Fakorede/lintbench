package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, Attr> mIds;

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        int slash = value.lastIndexOf('/');
        String idName = slash != -1 ? value.substring(slash + 1) : value;

        Attr first = mIds.get(idName);
        if (first != null) {
            String message = String.format("Duplicate id `%s`, already defined earlier in this layout", value);
            context.report(ISSUE, context.getLocation(attribute), message);
        } else {
            mIds.put(idName, attribute);
        }
    }
}