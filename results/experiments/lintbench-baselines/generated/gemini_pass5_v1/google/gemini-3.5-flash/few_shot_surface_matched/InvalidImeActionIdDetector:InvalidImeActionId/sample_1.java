package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class InvalidImeActionIdDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`."
                            + " It must be an integer constant, or an integer resource reference,"
                            + " as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class,
                            Scope.LAYOUT_RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if ("http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            String value = attribute.getValue();
            if (value != null && (value.startsWith("@id/")
                    || value.startsWith("@+id/")
                    || value.startsWith("@android:id/")
                    || value.startsWith("@*android:id/"))) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "`android:imeActionId` should be an integer constant, not a resource ID");
            }
        }
    }
}