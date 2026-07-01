package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import java.util.Collection;
import java.util.Collections;

public class InvalidImeActionIdDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "InvalidImeActionId",
        "Invalid imeActionId declaration",
        "`android:imeActionId` should not be a resource ID such as `@+id/resName`. It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value.startsWith("@id/") || value.startsWith("@+id/")) {
            context.report(ISSUE, context.getLocation(attribute),
                "Invalid `android:imeActionId`: must be an integer constant or `@integer` resource reference, not an ID reference");
        }
    }
}