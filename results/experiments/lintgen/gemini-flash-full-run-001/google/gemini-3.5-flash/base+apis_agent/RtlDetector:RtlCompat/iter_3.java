package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_ALIGNMENT)) {
            int minSdk = 1;
            if (context.getProject() != null && context.getProject().getMinSdk() != null) {
                minSdk = context.getProject().getMinSdk().getFeatureLevel();
            }
            if (minSdk < 17) {
                int folderVersion = context.getFolderVersion();
                if (folderVersion >= 17) {
                    return;
                }
                if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY)
                        && !element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
                    Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_ALIGNMENT);
                    if (attribute != null) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                "To support older versions than API 17, you must also specify a gravity or layout_gravity attribute"
                        );
                    }
                }
            }
        }
    }
}