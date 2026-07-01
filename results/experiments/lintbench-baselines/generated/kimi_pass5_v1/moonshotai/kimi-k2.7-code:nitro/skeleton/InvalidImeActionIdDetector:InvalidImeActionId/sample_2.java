package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_IME_ACTION_ID = "imeActionId";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` must be an integer constant or an integer resource reference "
                            + "(e.g. `@integer/action_id`). It must not be a view ID reference such as "
                            + "`@+id/viewId` or `@id/viewId`, because the value is passed to "
                            + "`EditorInfo.actionId` as a raw integer identifier.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

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
        if (value == null) {
            return;
        }

        if (isIdReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid `imeActionId` value: `" + value + "`. "
                            + "It must be an integer constant or an integer resource reference, "
                            + "not an ID resource reference.");
        }
    }

    private static boolean isIdReference(String value) {
        return value.startsWith("@+id/") || value.startsWith("@id/");
    }
}