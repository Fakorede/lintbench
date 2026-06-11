package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RtlDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlPaddingSymmetry",
            "Checks for padding and margin symmetry in layouts.",
            "If you specify padding or margin on the left side of a layout, you should probably also specify padding on the right side (and vice versa) for right-to-left layout symmetry.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final Set<String> LEFT_RIGHT_ATTRIBUTES = new HashSet<>(Arrays.asList(
            SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT,
            SdkConstants.ATTR_MARGIN_LEFT, SdkConstants.ATTR_MARGIN_RIGHT));

    @NonNull
    @Override
    public List<String> getApplicableAttributes() {
        return Arrays.asList(SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT,
                SdkConstants.ATTR_MARGIN_LEFT, SdkConstants.ATTR_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Element element, @NonNull String name, @NonNull String value) {
        if (LEFT_RIGHT_ATTRIBUTES.contains(name)) {
            checkSymmetry(context, element, name);
        }
    }

    private void checkSymmetry(XmlContext context, Element element, String attrName) {
        boolean isLeft = attrName.endsWith(SdkConstants.ATTR_PADDING_LEFT)
                || attrName.endsWith(SdkConstants.ATTR_MARGIN_LEFT);

        String oppositeAttr;
        if (isLeft) {
            oppositeAttr = attrName.replace(SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT)
                    .replace(SdkConstants.ATTR_MARGIN_LEFT, SdkConstants.ATTR_MARGIN_RIGHT);
        } else {
            oppositeAttr = attrName.replace(SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING_LEFT)
                    .replace(SdkConstants.ATTR_MARGIN_RIGHT, SdkConstants.ATTR_MARGIN_LEFT);
        }

        if (!element.hasAttribute(oppositeAttr)) {
            Location location = context.getLocation(element.getAttributeNode(attrName));
            String message = "If you specify padding or margin on the left side of a layout, you should probably also specify padding/margin on the right side (and vice versa) for right-to-left layout symmetry.";
            context.report(ISSUE, element, location, message);
        }
    }

}