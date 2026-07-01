package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent "
                            + "by your app, which could impact the privacy of your users. Consider "
                            + "nesting your app's `trust-anchors` inside a `<debug-overrides>` "
                            + "element to make sure they are only available when "
                            + "`android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
                    .addMoreInfo("https://developer.android.com/training/articles/security-config#TrustingDebugCa");

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

        // Check if this is a network-security-config file
        if (!"network-security-config".equals(root.getTagName())) {
            return;
        }

        // Look for base-config and domain-config elements (not inside debug-overrides)
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element root) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tagName = element.getTagName();

            // Skip debug-overrides - user certs are fine there
            if ("debug-overrides".equals(tagName)) {
                continue;
            }

            // Check base-config and domain-config for trust-anchors with user certificates
            if ("base-config".equals(tagName) || "domain-config".equals(tagName)) {
                checkForUserCertificates(context, element);
                // Also recursively check nested domain-config elements
                checkNestedDomainConfigs(context, element);
            }
        }
    }

    private void checkNestedDomainConfigs(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("domain-config".equals(childElement.getTagName())) {
                checkForUserCertificates(context, childElement);
                checkNestedDomainConfigs(context, childElement);
            }
        }
    }

    private void checkForUserCertificates(@NonNull XmlContext context, @NonNull Element configElement) {
        NodeList children = configElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if ("trust-anchors".equals(element.getTagName())) {
                checkTrustAnchors(context, element);
            }
        }
    }

    private void checkTrustAnchors(@NonNull XmlContext context, @NonNull Element trustAnchors) {
        NodeList children = trustAnchors.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if ("certificates".equals(element.getTagName())) {
                String src = element.getAttributeNS(null, "src");
                if (src == null || src.isEmpty()) {
                    // Try without namespace
                    src = element.getAttribute("src");
                }
                if ("user".equals(src)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Allowing user certificates could allow eavesdroppers to intercept "
                                    + "data sent by your app, which could impact the privacy of "
                                    + "your users. Consider nesting your app's `trust-anchors` "
                                    + "inside a `<debug-overrides>` element to make sure they are "
                                    + "only available when `android:debuggable` is set to `true`.");
                }
            }
        }
    }
}