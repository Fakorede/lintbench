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

import java.util.Collection;
import java.util.Collections;

import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid `android:imeActionId` declaration",
            "`android:imeActionId` must be set to an integer constant or an integer resource "
                    + "reference (e.g. `@integer/action_send`). ID resource references such as "
                    + "`@+id/resName` are not valid because `EditorInfo.imeActionId` expects a "
                    + "raw integer action id.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null) {
            if (url.type == ResourceType.ID) {
                reportInvalidValue(context, attribute, value);
            }
        } else if (!isIntegerConstant(value)) {
            reportInvalidValue(context, attribute, value);
        }
    }

    private static boolean isIntegerConstant(@NonNull String value) {
        try {
            Integer.decode(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void reportInvalidValue(@NonNull XmlContext context, @NonNull Attr attribute,
            @NonNull String value) {
        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Invalid `android:imeActionId`: `" + value
                        + "` is not an integer constant or an integer resource reference"
        );
    }
}