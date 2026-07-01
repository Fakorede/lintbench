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
        "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
        "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String ns = attribute.getNamespaceURI();
        if (ns != null && !SdkConstants.ANDROID_URI.equals(ns)) {
            return;
        }

        String value = attribute.getValue().trim();
        if (value.startsWith("@id/") || value.startsWith("@+id/") || value.startsWith("@android:id/")) {
            String message = String.format(
                "Invalid `android:imeActionId`: `%s` is an ID resource, but `imeActionId` requires an integer constant or `@integer/` resource reference",
                value);
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }
}