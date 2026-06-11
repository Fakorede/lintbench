package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlTextAlignment",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, if you are supporting older versions than API 17, you must also specify a gravity or layout_gravity attribute, since older platforms will ignore the `textAlignment` attribute.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr textAlignmentAttr = getAttribute(element, SdkConstants.ATTR_TEXT_ALIGNMENT);
        if (textAlignmentAttr != null) {
            boolean hasGravityOrLayoutGravity = false;
            for (String attrName : new String[]{SdkConstants.ATTR_GRAVITY, SdkConstants.ATTR_LAYOUT_GRAVITY}) {
                Attr attr = getAttribute(element, attrName);
                if (attr != null) {
                    hasGravityOrLayoutGravity = true;
                    break;
                }
            }

            if (!hasGravityOrLayoutGravity) {
                context.report(ISSUE, element, context.getLocation(textAlignmentAttr), "You must also specify a gravity or layout_gravity attribute for compatibility with API levels below 17.");
            }
        }
    }

    private Attr getAttribute(Element element, String attrName) {
        return (Attr) element.getAttributeNode(attrName);
    }
}