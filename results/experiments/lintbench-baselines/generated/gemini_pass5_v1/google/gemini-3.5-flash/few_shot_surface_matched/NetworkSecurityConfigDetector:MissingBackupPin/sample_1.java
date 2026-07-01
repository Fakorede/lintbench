package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    "It is highly recommended to declare a backup `<pin>` element. Not having "
                            + "a second pin defined can cause connection failures when the "
                            + "particular site certificate is rotated and the app has not yet "
                            + "been updated.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        if (!"network-security-config".equals(root.getTagName())) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, org.w3c.dom.Element element) {
        if ("pin-set".equals(element.getTagName())) {
            checkPinSet(context, element);
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child);
            }
        }
    }

    private void checkPinSet(XmlContext context, org.w3c.dom.Element pinSet) {
        int pinCount = 0;
        org.w3c.dom.NodeList children = pinSet.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
                if ("pin".equals(childElement.getTagName())) {
                    pinCount++;
                }
            }
        }
        if (pinCount < 2) {
            context.report(
                    ISSUE,
                    pinSet,
                    context.getNameLocation(pinSet),
                    "Missing backup pin; at least two pins must be provided for reliability");
        }
    }
}