package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_IME_ACTION_ID;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends ResourceXmlDetector {

    private static final String ISSUE_ID = "InvalidImeActionId";
    private static final String ISSUE_SUMMARY = "Invalid imeActionId declaration";
    private static final String ISSUE_EXPLANATION =
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                    + "It must be an integer constant, or an integer resource reference, "
                    + "as defined in `EditorInfo`.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_SUMMARY,
            ISSUE_EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    ).addMoreInfo("https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (isIdReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid value for `android:imeActionId`: must be an integer constant "
                            + "or an integer resource reference (`@integer/...`), not an ID reference"
            );
        }
    }

    private static boolean isIdReference(@NonNull String value) {
        if (value.isEmpty() || value.charAt(0) != '@') {
            return false;
        }

        int start = 1;
        if (value.length() > 1 && value.charAt(1) == '+') {
            start = 2;
        }

        int slash = value.indexOf('/', start);
        if (slash == -1) {
            return false;
        }

        int colon = value.indexOf(':', start);
        int typeStart = start;
        if (colon != -1 && colon < slash) {
            typeStart = colon + 1;
        }

        String type = value.substring(typeStart, slash);
        return "id".equals(type);
    }
}