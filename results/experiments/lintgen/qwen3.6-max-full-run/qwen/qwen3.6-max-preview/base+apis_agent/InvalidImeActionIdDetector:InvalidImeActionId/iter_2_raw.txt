package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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

public class InvalidImeActionIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
            "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
            Category.CORRECTNESS, 5, Severity.WARNING,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            value = value.trim();
            if (value.startsWith("@+id/") || value.startsWith("@id/") || value.startsWith("@android:id/")) {
                context.report(ISSUE, context.getLocation(attribute),
                        "Invalid imeActionId: should be an integer constant or integer resource reference, not an ID reference");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }
}