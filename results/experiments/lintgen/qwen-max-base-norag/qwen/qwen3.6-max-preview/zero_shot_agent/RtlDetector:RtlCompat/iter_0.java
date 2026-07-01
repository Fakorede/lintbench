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
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getMainProject().getMinSdkVersion().getApiLevel() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, "gravity");
        boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");

        if (!hasGravity && !hasLayoutGravity) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "When using `textAlignment` on older platforms, also specify `gravity` or `layout_gravity` for compatibility");
        }
    }
}