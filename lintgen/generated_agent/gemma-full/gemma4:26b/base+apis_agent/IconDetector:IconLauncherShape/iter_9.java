package com.android.tools.lint.checks;

import java.util.Collection;
import java.util.Collections;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconSilhouette",
            "Launcher icon lacks a distinct silhouette",
            "According to the Android Design Guide, launcher icons should use a \"distinct silhouette\" " +
                    "(a three-dimensional, front view with depth). Using a solid color for the foreground " +
                    "prevents this.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("adaptive-icon");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String childTagName = child.getLocalName();
                if (childTagName == null) {
                    childTagName = child.getTagName();
                }

                if ("foreground".equals(childTagName)) {
                    for (int j = 0; j < child.getAttributes().getLength(); j++) {
                        Attr attr = (Attr) child.getAttributes().item(j);
                        String attrName = attr.getName();
                        String attrValue = attr.getValue();

                        if (attrName.endsWith("drawable") && 
                            (attrValue.startsWith("@color/") || attrValue.startsWith("@android:color/"))) {
                            context.report(
                                    ISSUE,
                                    child,
                                    context.getLocation(child),
                                    "The foreground of the adaptive icon is set to a color resource. " +
                                            "A distinct silhouette requires a vector or bitmap with transparency."
                            );
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }
}