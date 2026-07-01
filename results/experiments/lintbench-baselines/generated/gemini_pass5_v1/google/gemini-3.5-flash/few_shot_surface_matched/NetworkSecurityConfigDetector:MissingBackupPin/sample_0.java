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
                    "It is highly recommended to declare a backup `<pin>` element. Not "
                            + "having a second pin defined can cause connection failures when the "
                            + "particular site certificate is rotated and the app has not yet "
                            + "been updated.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        org.w3c.dom.NodeList pinSets = document.getElementsByTagName("pin-set");
        for (int i = 0; i < pinSets.getLength(); i++) {
            org.w3c.dom.Element pinSet = (org.w3c.dom.Element) pinSets.item(i);
            int pinCount = 0;
            org.w3c.dom.NodeList children = pinSet.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "pin".equals(child.getNodeName())) {
                    pinCount++;
                }
            }
            if (pinCount == 1) {
                context.report(
                        ISSUE,
                        pinSet,
                        context.getNameLocation(pinSet),
                        "Missing backup PIN. It is highly recommended to declare a backup `<pin>` element."
                );
            }
        }
    }
}