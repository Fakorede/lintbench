package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
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

    public static final Issue MISSING_BACKUP_PIN =
            Issue.create(
                    "MissingBackupPin",
                    "Missing Backup Pin",
                    "It is highly recommended to declare a backup `<pin>` element. "
                            + "Not having a second pin defined can cause connection failures when the "
                            + "particular site certificate is rotated and the app has not yet been updated.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/preview/features/security-config.html");

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do before checking the root project
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Only process network-security-config files
        if (!"network-security-config".equals(root.getTagName())) {
            return;
        }

        // Walk through all domain-config elements (including nested ones)
        checkDomainConfigs(context, root);
    }

    private void checkDomainConfigs(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if ("domain-config".equals(tagName)) {
                // Check for pin-set within this domain-config
                checkPinSet(context, childElement);
                // Recurse into nested domain-config elements
                checkDomainConfigs(context, childElement);
            }
        }
    }

    private void checkPinSet(@NonNull XmlContext context, @NonNull Element domainConfig) {
        NodeList children = domainConfig.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("pin-set".equals(childElement.getTagName())) {
                checkPins(context, childElement);
            }
        }
    }

    private void checkPins(@NonNull XmlContext context, @NonNull Element pinSet) {
        int pinCount = 0;
        NodeList children = pinSet.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("pin".equals(childElement.getTagName())) {
                pinCount++;
            }
        }

        if (pinCount < 2) {
            Location location = context.getLocation(pinSet);
            context.report(
                    MISSING_BACKUP_PIN,
                    pinSet,
                    location,
                    "A `<pin-set>` should contain at least two `<pin>` elements to ensure "
                            + "a backup pin is available during certificate rotation");
        }
    }
}