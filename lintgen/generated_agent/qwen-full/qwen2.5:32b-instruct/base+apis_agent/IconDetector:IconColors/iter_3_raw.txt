package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Document;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconColors",
            "Notification icons and Action Bar icons should only be white and shades of gray.",
            "See the Android Design Guide for more details. Note that Lint decides whether an icon is an action bar icon or a notification icon based on the filename prefix: `ic_menu_` for action bar icons, `ic_stat_` for notification icons etc.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        NodeList nodes = document.getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String name = getIconName(element);
                if (name != null && !isValidColor(name)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The icon color does not follow the recommended visual style.");
                }
            }
        }
    }

    private String getIconName(Element element) {
        Node nameNode = element.getAttributes().getNamedItem("android:name");
        if (nameNode != null) {
            return nameNode.getNodeValue();
        }
        return null;
    }

    private boolean isValidColor(String iconName) {
        // Check for action bar icons and notification icons
        if (iconName.startsWith("ic_menu_") || iconName.startsWith("ic_stat_")) {
            // Validate that the icon is white or shades of gray
            // This is a placeholder logic, actual implementation would require image processing
            return false;  // Placeholder validation
        }
        return true;
    }

}