package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
                    "if you are supporting older versions than API 17, you must also specify a " +
                    "gravity or layout_gravity attribute, since older platforms will ignore the " +
                    "`textAlignment` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

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
        if (context.getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (gravity.isEmpty() && layoutGravity.isEmpty()) {
            String message = String.format(
                    "To support older versions than API 17 (project specifies %d) you should *also* specify gravity or layout_gravity",
                    context.getMinSdk());
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}