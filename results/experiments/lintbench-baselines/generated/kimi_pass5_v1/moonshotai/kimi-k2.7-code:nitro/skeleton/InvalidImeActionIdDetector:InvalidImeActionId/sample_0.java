package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "The `android:imeActionId` attribute must be an integer constant or a reference to an integer resource (e.g. `@integer/...`). "
                            + "It must not be a resource ID such as `@+id/...` or `@id/...`, because `EditorInfo.actionId` is an arbitrary integer used to identify the action, not a view identifier.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final Pattern INTEGER_LITERAL_PATTERN =
            Pattern.compile("[+-]?(?:0[xX][0-9a-fA-F]+|[0-9]+)");

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (isValidImeActionId(value)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && SdkConstants.ID_PREFIX.regionMatches(1, url.type, 0, url.type.length())) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid `android:imeActionId`: must be an integer constant or integer resource reference, not a resource ID such as `@+id/...`");
        } else {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid `android:imeActionId`: must be an integer constant or integer resource reference");
        }
    }

    private static boolean isValidImeActionId(String value) {
        if (INTEGER_LITERAL_PATTERN.matcher(value).matches()) {
            return true;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        return url != null && SdkConstants.TAG_INTEGER.equals(url.type);
    }
}