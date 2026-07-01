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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "RtlCompat",
        "Right-to-left text compatibility issues",
        "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
        "if you are supporting older versions than API 17, you must **also** specify a " +
        "gravity or layout_gravity attribute, since older platforms will ignore the " +
        "`textAlignment` attribute.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int RTL_API = 17;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            return;
        }

        boolean hasTextAlignment = false;
        boolean hasGravity = false;
        boolean hasLayoutGravity = false;
        Attr textAlignmentAttr = null;

        if (element.hasAttributes()) {
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                Attr attr = (Attr) element.getAttributes().item(i);
                if (!SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())) {
                    continue;
                }
                String name = attr.getLocalName();
                if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
                    hasTextAlignment = true;
                    textAlignmentAttr = attr;
                } else if ("gravity".equals(name)) {
                    hasGravity = true;
                } else if ("layout_gravity".equals(name)) {
                    hasLayoutGravity = true;
                } else if (name.contains("Start")) {
                    String fallback = name.replace("Start", "Left");
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, fallback)) {
                        context.report(ISSUE, context.getLocation(attr),
                            "Consider adding `android:" + fallback + "` for compatibility with API < 17");
                    }
                } else if (name.contains("End")) {
                    String fallback = name.replace("End", "Right");
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, fallback)) {
                        context.report(ISSUE, context.getLocation(attr),
                            "Consider adding `android:" + fallback + "` for compatibility with API < 17");
                    }
                }
            }
        }

        if (hasTextAlignment && !hasGravity && !hasLayoutGravity) {
            context.report(ISSUE, context.getLocation(textAlignmentAttr),
                "Consider adding `android:gravity` or `android:layout_gravity` for compatibility with API < 17");
        }
    }
}