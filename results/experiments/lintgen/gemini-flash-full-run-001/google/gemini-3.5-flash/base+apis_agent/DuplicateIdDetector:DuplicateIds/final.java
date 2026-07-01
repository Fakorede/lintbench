package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.NAVIGATION;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            Map<String, Element> idToElement = new HashMap<>();
            findDuplicateIds(context, root, idToElement);
        }
    }

    private void findDuplicateIds(XmlContext context, Element element, Map<String, Element> idToElement) {
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            String id = idValue;
            if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
                id = id.substring(SdkConstants.NEW_ID_PREFIX.length());
            } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
                id = id.substring(SdkConstants.ID_PREFIX.length());
            }

            if (!id.isEmpty()) {
                if (idToElement.containsKey(id)) {
                    context.report(
                            ISSUE,
                            idAttr,
                            context.getLocation(idAttr),
                            String.format("Duplicate id `%s`, already defined earlier in this layout", idValue)
                    );
                } else {
                    idToElement.put(id, element);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                findDuplicateIds(context, (Element) child, idToElement);
            }
        }
    }
}