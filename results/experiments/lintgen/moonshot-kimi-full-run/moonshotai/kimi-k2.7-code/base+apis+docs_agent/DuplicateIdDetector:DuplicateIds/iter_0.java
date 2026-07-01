package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate IDs in layout",
            "Within a layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        Map<String, List<Element>> idMap = new HashMap<>();
        collectIds(document.getDocumentElement(), idMap);

        for (Map.Entry<String, List<Element>> entry : idMap.entrySet()) {
            List<Element> elements = entry.getValue();
            if (elements.size() <= 1) {
                continue;
            }

            String id = entry.getKey();
            Element first = elements.get(0);
            Location firstLocation = context.getLocation(first);

            for (int i = 1; i < elements.size(); i++) {
                Element duplicate = elements.get(i);
                Location location = context.getLocation(duplicate);
                location.setSecondary(firstLocation);
                context.report(ISSUE, duplicate, location,
                        "Duplicate id `" + id + "`, already defined in this layout");
            }
        }
    }

    private static void collectIds(Element element, Map<String, List<Element>> idMap) {
        if (element == null) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            idMap.computeIfAbsent(id, k -> new ArrayList<>()).add(element);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, idMap);
            }
        }
    }
}