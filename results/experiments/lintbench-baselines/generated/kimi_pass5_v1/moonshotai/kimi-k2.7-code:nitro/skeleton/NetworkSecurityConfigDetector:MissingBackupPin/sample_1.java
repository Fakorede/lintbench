package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing Backup Pin",
                    "Certificate pinning is configured by declaring one or more <pin> elements inside a <pin-set>. It is strongly recommended to include at least one backup pin (a second <pin> element) in each pin-set. If the remote site rotates its certificate and the new certificate is not one of the configured pins, the app will be unable to connect until it is updated. Declaring a backup pin reduces this risk.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context,
                              @com.android.annotations.NonNull org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        org.w3c.dom.NodeList pinSets = document.getElementsByTagName("pin-set");
        for (int i = 0; i < pinSets.getLength(); i++) {
            org.w3c.dom.Node pinSetNode = pinSets.item(i);
            if (pinSetNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }

            org.w3c.dom.Element pinSet = (org.w3c.dom.Element) pinSetNode;
            int pinCount = 0;

            org.w3c.dom.NodeList pins = pinSet.getElementsByTagName("pin");
            for (int j = 0; j < pins.getLength(); j++) {
                org.w3c.dom.Node pinNode = pins.item(j);
                if (pinNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    pinCount++;
                }
            }

            if (pinCount == 1) {
                context.report(
                        ISSUE,
                        context.getLocation(pinSet),
                        "This <pin-set> contains only one <pin>; add a backup pin so certificate rotation does not break connections before the app is updated.");
            }
        }
    }
}