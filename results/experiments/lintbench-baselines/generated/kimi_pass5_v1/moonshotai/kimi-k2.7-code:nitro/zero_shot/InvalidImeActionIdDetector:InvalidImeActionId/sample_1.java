package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_IME_ACTION_ID;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
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
            "Invalid `imeActionId` declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                    + "It must be an integer constant, or an integer resource reference, "
                    + "as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceUri())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && url.type == ResourceType.ID) {
            context.report(
                    ISSUE,
                    context.getValueLocation(attribute),
                    "Invalid value for `android:imeActionId`: must be an integer constant or "
                            + "integer resource reference, not an id reference.");
        }
    }
}