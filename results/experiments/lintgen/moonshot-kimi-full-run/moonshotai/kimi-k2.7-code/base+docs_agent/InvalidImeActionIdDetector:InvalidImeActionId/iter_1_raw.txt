package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;

public class InvalidImeActionIdDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid `android:imeActionId` declaration",
            "`android:imeActionId` must be set to an integer constant or an integer resource "
                    + "reference. It should not be a resource ID such as `@+id/resName`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return;
        }

        if (isIntegerConstant(value)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null) {
            if (url.type == ResourceType.INTEGER) {
                return;
            }
            reportInvalid(context, attribute, value, url.type == ResourceType.ID);
            return;
        }

        // Some versions of ResourceUrl do not parse @+id/..., so check explicitly.
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            reportInvalid(context, attribute, value, true);
            return;
        }

        reportInvalid(context, attribute, value, false);
    }

    private static boolean isIntegerConstant(String value) {
        return value.matches("[+-]?(0[xX][0-9a-fA-F]+|\\d+)");
    }

    private static void reportInvalid(
            XmlContext context,
            Attr attribute,
            String value,
            boolean isIdReference) {
        String message;
        if (isIdReference) {
            message = "Invalid value for `android:imeActionId`: expected an integer constant or "
                    + "integer resource reference, but found an ID resource reference (`"
                    + value + "`)";
        } else {
            message = "Invalid value for `android:imeActionId`: expected an integer constant or "
                    + "integer resource reference, but found `"
                    + value + "`";
        }
        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                message);
    }
}