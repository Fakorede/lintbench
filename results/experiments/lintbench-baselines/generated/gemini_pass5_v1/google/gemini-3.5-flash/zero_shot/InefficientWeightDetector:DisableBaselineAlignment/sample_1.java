package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between "
                    + "nested layouts, the baseline alignment property should be turned off to "
                    + "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED)) {
            return;
        }

        boolean hasLayoutChildWithWeight = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String childTag = childElement.getTagName();
                if (childTag.endsWith("Layout") && childElement.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    hasLayoutChildWithWeight = true;
                    break;
                }
            }
        }

        if (hasLayoutChildWithWeight) {
            LintFix fix = fix()
                    .set(ANDROID_URI, ATTR_BASELINE_ALIGNED, "false")
                    .build();

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance",
                    fix
            );
        }
    }
}