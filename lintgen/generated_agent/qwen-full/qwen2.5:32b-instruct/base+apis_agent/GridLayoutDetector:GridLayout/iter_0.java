package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class GridLayoutDetector extends Detector implements XmlScanner {

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("GridLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int rowCount = getAttributeValueAsInt(element, "rowCount", 0);
        int columnCount = getAttributeValueAsInt(element, "columnCount", 0);

        if (element.hasChildNodes()) {
            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                Element childElement = (Element) element.getChildNodes().item(i);
                checkChildElement(context, childElement, rowCount, columnCount);
            }
        }
    }

    private void checkChildElement(XmlContext context, Element element, int rowCount, int columnCount) {
        if (element.getNodeName().equals("android.widget.FrameLayout") || element.getNodeName().equals("android.widget.Button")) {
            int layoutRow = getAttributeValueAsInt(element, "layout_row", -1);
            int layoutColumn = getAttributeValueAsInt(element, "layout_column", -1);

            if (layoutRow >= rowCount) {
                context.report(
                        this,
                        element,
                        context.getLocation(element),
                        "The value of 'layout_row' is greater than or equal to the declared 'rowCount'."
                );
            }

            if (layoutColumn >= columnCount) {
                context.report(
                        this,
                        element,
                        context.getLocation(element),
                        "The value of 'layout_column' is greater than or equal to the declared 'columnCount'."
                );
            }
        }
    }

    private int getAttributeValueAsInt(Element element, String attributeName, int defaultValue) {
        Attr attribute = (Attr) element.getAttributeNode(attributeName);
        if (attribute != null && !attribute.getValue().isEmpty()) {
            try {
                return Integer.parseInt(attribute.getValue());
            } catch (NumberFormatException e) {
                // Handle the case where parsing fails
            }
        }
        return defaultValue;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}

    @Override
    public void visitDocument(XmlContext context, Document document) {}
}