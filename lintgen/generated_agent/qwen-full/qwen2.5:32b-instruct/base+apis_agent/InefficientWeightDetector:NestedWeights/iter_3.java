package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlScanner;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InefficientWeight",
            "Nested layout weights require multiple measurements, which can be inefficient.",
            "Layout weights require a widget to be measured twice. When a `LinearLayout` with non-zero weights is nested inside another `LinearLayout` with non-zero weights, then the number of measurements increase exponentially.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Set<String> linearLayoutStack = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getTagName().equals("LinearLayout")) {
            String weightSumAttr = getAttributeValue(element, "android:weightSum");

            if ((weightSumAttr != null && !weightSumAttr.equals("0"))
                    || hasNonZeroWeights(context, element)) {
                linearLayoutStack.add(element.getAttribute("android:id"));
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (element.getTagName().equals("LinearLayout")) {
            linearLayoutStack.remove(element.getAttribute("android:id"));

            // Check for nested LinearLayout with weights
            if (!linearLayoutStack.isEmpty()) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Nested `LinearLayout` with non-zero weights can be inefficient.");
            }
        }
    }

    private boolean hasNonZeroWeights(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && ((Element) child).getTagName().equals("LinearLayout")) {
                String weightAttr = getAttributeValue((Element) child, "android:layout_weight");
                if (weightAttr != null && !weightAttr.equals("0")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getAttributeValue(Element element, String attributeName) {
        Attr attr = (Attr) element.getAttributeNode(attributeName);
        return attr == null ? null : attr.getValue();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}