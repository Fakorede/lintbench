package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing Backup Pin",
                    "It is highly recommended to declare a backup <pin> element. Not having a "
                            + "second pin defined can cause connection failures when the "
                            + "particular site certificate is rotated and the app has not yet "
                            + "been updated.",
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
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        NodeList pinSets = root.getElementsByTagName("pin-set");
        for (int i = 0; i < pinSets.getLength(); i++) {
            Element pinSet = (Element) pinSets.item(i);
            int pinCount = 0;
            NodeList children = pinSet.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "pin".equals(((Element) child).getTagName())) {
                    pinCount++;
                }
            }

            if (pinCount < 2) {
                context.report(
                        ISSUE,
                        pinSet,
                        context.getLocation(pinSet),
                        "Missing backup pin: a <pin-set> should contain at least two <pin> "
                                + "elements to avoid connection failures when a certificate is "
                                + "rotated.");
            }
        }
    }
}