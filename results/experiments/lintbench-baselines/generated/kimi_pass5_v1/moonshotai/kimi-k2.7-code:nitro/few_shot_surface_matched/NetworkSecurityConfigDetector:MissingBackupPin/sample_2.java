package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing Backup Pin",
                    "It is highly recommended to declare a backup `<pin>` element. Not having a"
                            + " second pin defined can cause connection failures when the"
                            + " particular site certificate is rotated and the app has not yet"
                            + " been updated.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No per-project state required.
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        if (!"network-security-config".equals(root.getNodeName())) {
            return;
        }

        checkPinSets(context, root);
    }

    private void checkPinSets(XmlContext context, org.w3c.dom.Element element) {
        if ("pin-set".equals(element.getNodeName())) {
            int pinCount = 0;
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                        && "pin".equals(child.getNodeName())) {
                    pinCount++;
                }
            }

            if (pinCount < 2) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Only one pin is configured in this pin-set; consider adding a backup"
                                + " pin to avoid connection failures during certificate"
                                + " rotation.");
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && child instanceof org.w3c.dom.Element) {
                checkPinSets(context, (org.w3c.dom.Element) child);
            }
        }
    }
}