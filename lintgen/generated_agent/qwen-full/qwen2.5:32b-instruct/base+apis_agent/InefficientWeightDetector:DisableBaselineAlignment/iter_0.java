package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Detector.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InefficientBaselineAlignment",
            "Missing `baselineAligned` attribute in LinearLayout with weights",
            "When a `LinearLayout` is used to distribute the space proportionally between nested layouts, " +
                    "the baseline alignment property should be turned off to make the layout computation faster.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, true));

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (SdkConstants.LAYOUT_WEIGHT.equals(element.getAttribute(SdkConstants.ATTR_LAYOUT_WEIGHT))) {
            boolean baselineAligned = Boolean.parseBoolean(element.getAttribute(SdkConstants.ATTR_BASELINE_ALIGNED));
            if (baselineAligned || !element.hasAttribute(SdkConstants.ATTR_BASELINE_ALIGNED)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Missing `baselineAligned` attribute in LinearLayout with weights");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}