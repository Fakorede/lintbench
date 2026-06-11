package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getTagName().equals("LinearLayout")) {
            boolean hasOrientation = false;
            for (Attr attr : getAttributes(element)) {
                if ("orientation".equals(attr.getName())) {
                    hasOrientation = true;
                    break;
                }
            }

            List<Element> children = getChildElements(element);
            int childCount = children.size();

            // Check for LinearLayout with multiple children and no orientation attribute
            if (!hasOrientation && childCount > 1) {
                context.report(this, element, context.getLocation(element),
                        "LinearLayout has multiple children but no explicit orientation");
            }

            // Check for empty LinearLayout with an id attribute
            boolean hasId = false;
            for (Attr attr : getAttributes(element)) {
                if ("id".equals(attr.getName())) {
                    hasId = true;
                    break;
                }
            }

            if (!hasOrientation && childCount == 0 && hasId) {
                context.report(this, element, context.getLocation(element),
                        "Empty LinearLayout with an id attribute but no explicit orientation");
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}

    @Override
    public void visitDocument(XmlContext context, Document document) {}

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}