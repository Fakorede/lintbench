package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                            + "It must be an integer constant, or an integer resource reference, "
                            + "as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    private static final String ATTR_IME_ACTION_ID = "imeActionId";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && "id".equals(url.type)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should be an integer constant or an integer resource "
                            + "reference; `id` resource references are not allowed here");
        }
    }
}