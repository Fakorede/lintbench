package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateIdDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            "return an unexpected view.",
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
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            Set<String> seenIds = new HashSet<>();
            checkNode(context, root, seenIds);
        }
    }

    private void checkNode(XmlContext context, Node node, Set<String> seenIds) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
            if (idAttr != null) {
                String idValue = idAttr.getValue();
                String id = stripIdPrefix(idValue);
                if (!id.isEmpty()) {
                    if (seenIds.contains(id)) {
                        context.report(
                                ISSUE,
                                idAttr,
                                context.getLocation(idAttr),
                                "Duplicate id `" + idValue + "`, already defined earlier in this layout"
                        );
                    } else {
                        seenIds.add(id);
                    }
                }
            }
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                checkNode(context, children.item(i), seenIds);
            }
        }
    }

    private String stripIdPrefix(String id) {
        if (id == null) {
            return "";
        }
        int index = id.indexOf('/');
        if (index != -1) {
            return id.substring(index + 1);
        }
        return id;
    }
}