package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. " +
            "However, if you are supporting older versions than API 17, you must " +
            "also specify a gravity or layout_gravity attribute, since older platforms " +
            "will ignore the `textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.LAYOUT_RESOURCE_FILES)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getProject().getMinSdk() >= 17) {
            return;
        }

        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (!hasGravity && !hasLayoutGravity) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17, you must also specify a `gravity` or `layout_gravity` attribute"
            );
        }
    }
}