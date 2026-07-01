package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {

    private static final String MESSAGE =
            "textAlignment was added in API level 17; when supporting older versions, "
                    + "you must also specify a gravity or layout_gravity attribute";

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The textAlignment attribute was added in API 17. When supporting older versions, "
                    + "you must also specify a gravity or layout_gravity attribute, since older "
                    + "platforms will ignore textAlignment.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (attribute.getValue().trim().isEmpty()) {
            return;
        }

        Project project = context.getMainProject();
        if (project.getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasGravity(element)) {
            return;
        }

        context.report(ISSUE, attribute, context.getValueLocation(attribute), MESSAGE);
    }

    private static boolean hasGravity(@NonNull Element element) {
        return !element.getAttributeNS(ANDROID_URI, ATTR_GRAVITY).trim().isEmpty()
                || !element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY).trim().isEmpty();
    }
}