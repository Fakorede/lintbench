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

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent"
                            + " by your app, which could impact the privacy of your users."
                            + " Consider nesting your app's `trust-anchors` inside a"
                            + " `<debug-overrides>` element to make sure they are only available"
                            + " when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
                    .addMoreInfo(
                            "https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_BASE_CONFIG = "base-config";
    private static final String TAG_DOMAIN_CONFIG = "domain-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    public NetworkSecurityConfigDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
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

        // Check base-config and domain-config elements (but not debug-overrides)
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tagName = element.getTagName();

            // Skip debug-overrides - user certs there are intentional
            if (TAG_DEBUG_OVERRIDES.equals(tagName)) {
                continue;
            }

            if (TAG_BASE_CONFIG.equals(tagName) || TAG_DOMAIN_CONFIG.equals(tagName)) {
                checkForUserCertificates(context, element);
            }
        }
    }

    private void checkForUserCertificates(@NonNull XmlContext context, @NonNull Element element) {
        // Look for trust-anchors within this element
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_TRUST_ANCHORS.equals(tagName)) {
                checkTrustAnchors(context, childElement);
            } else if (TAG_DOMAIN_CONFIG.equals(tagName)) {
                // Nested domain-config
                checkForUserCertificates(context, childElement);
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
            Element childElement = (Element) child;
            if (TAG_CERTIFICATES.equals(childElement.getTagName())) {
                String src = childElement.getAttribute(ATTR_SRC);
                if (VALUE_USER.equals(src)) {
                    Location location = context.getElementLocation(childElement);
                    context.report(
                            ISSUE,
                            childElement,
                            location,
                            "Allowing user certificates could allow eavesdroppers to intercept"
                                    + " data sent by your app, which could impact the privacy of"
                                    + " your users. Consider nesting your app's `trust-anchors`"
                                    + " inside a `<debug-overrides>` element to make sure they"
                                    + " are only available when `android:debuggable` is set to"
                                    + " `true`.");
                }
            }
        }
    }
}