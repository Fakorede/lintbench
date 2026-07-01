package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class InvalidImeActionIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
            "It must be an integer constant, or an integer resource reference, as defined " +
            "in `EditorInfo`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    public InvalidImeActionIdDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Check if the value looks like a new ID declaration: @+id/...
        // or a plain ID reference: @id/...
        // These are invalid for imeActionId which should be an integer constant
        // or an integer resource reference (e.g. @integer/...)
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "`android:imeActionId` should not be a resource ID reference (`" + value +
                    "`). It must be an integer constant or an integer resource reference " +
                    "(e.g. `@integer/myActionId`), as defined in `EditorInfo`."
            );
        }
    }
}