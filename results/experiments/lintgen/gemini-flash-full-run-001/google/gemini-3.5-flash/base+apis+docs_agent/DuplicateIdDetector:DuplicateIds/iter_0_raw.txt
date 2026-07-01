package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Map<String, Attr> seenIds = new HashMap<>();
        visitElement(context, root, seenIds);
    }

    private void visitElement(XmlContext context, Element element, Map<String, Attr> seenIds) {
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String rawValue = idAttr.getValue();
            String id = stripIdPrefix(rawValue);
            if (id != null && !id.isEmpty()) {
                if (seenIds.containsKey(id)) {
                    Attr firstAttr = seenIds.get(id);
                    String message = String.format("Duplicate id `%s`, already defined earlier in this layout", rawValue);
                    
                    Location location = context.getLocation(idAttr);
                    Location secondary = context.getLocation(firstAttr);
                    secondary.setMessage("First definition here");
                    location.setSecondary(secondary);
                    
                    context.report(ISSUE, idAttr, location, message);
                } else {
                    seenIds.put(id, idAttr);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child, seenIds);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id == null) {
            return null;
        }
        int index = id.indexOf('/');
        if (index >= 0) {
            return id.substring(index + 1);
        }
        return id;
    }
}