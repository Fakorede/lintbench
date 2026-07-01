package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

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

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_DOMAIN_CONFIG = "domain-config";
    private static final String TAG_PIN_SET = "pin-set";
    private static final String TAG_PIN = "pin";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Project project) {
        // Nothing to do before checking root project
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        if (!TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }

        checkDomainConfigs(context, root);
    }

    private void checkDomainConfigs(@NonNull XmlContext context, @NonNull Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tagName = element.getTagName();

            if (TAG_DOMAIN_CONFIG.equals(tagName)) {
                checkPinSet(context, element);
                // Recurse into nested domain-config elements
                checkDomainConfigs(context, element);
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
            Element element = (Element) child;
            if (TAG_PIN_SET.equals(element.getTagName())) {
                checkPinCount(context, element);
            }
        }
    }

    private void checkPinCount(@NonNull XmlContext context, @NonNull Element pinSet) {
        int pinCount = 0;
        NodeList children = pinSet.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (TAG_PIN.equals(element.getTagName())) {
                pinCount++;
            }
        }

        if (pinCount < 2) {
            context.report(
                    MISSING_BACKUP_PIN,
                    pinSet,
                    context.getLocation(pinSet),
                    "A backup `<pin>` declaration is highly recommended to avoid connection "
                            + "failures if the certificate is rotated");
        }
    }
}