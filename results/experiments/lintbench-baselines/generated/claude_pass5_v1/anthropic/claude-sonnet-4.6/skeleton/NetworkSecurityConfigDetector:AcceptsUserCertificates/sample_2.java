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
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by "
                            + "your app, which could impact the privacy of your users. Consider nesting "
                            + "your app's `trust-anchors` inside a `<debug-overrides>` element to make "
                            + "sure they are only available when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION)
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

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op: we handle everything in visitDocument
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

        // Check base-config and domain-config elements (but NOT debug-overrides)
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tagName = element.getTagName();

            // Skip debug-overrides — user certs there are intentional
            if (TAG_DEBUG_OVERRIDES.equals(tagName)) {
                continue;
            }

            if (TAG_BASE_CONFIG.equals(tagName) || TAG_DOMAIN_CONFIG.equals(tagName)) {
                checkForUserCertificates(context, element);
            }
        }
    }

    /**
     * Recursively checks an element (base-config or domain-config) for trust-anchors
     * that reference user certificates outside of debug-overrides.
     */
    private void checkForUserCertificates(@NonNull XmlContext context, @NonNull Element element) {
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

    /**
     * Checks a trust-anchors element for certificates with src="user".
     */
    private void checkTrustAnchors(@NonNull XmlContext context, @NonNull Element trustAnchors) {
        NodeList children = trustAnchors.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (TAG_CERTIFICATES.equals(element.getTagName())) {
                String src = element.getAttribute(ATTR_SRC);
                if (VALUE_USER.equals(src)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Allowing user certificates could allow eavesdroppers to intercept "
                                    + "data sent by your app, which could impact the privacy of your "
                                    + "users. Consider nesting your app's `trust-anchors` inside a "
                                    + "`<debug-overrides>` element to make sure they are only available "
                                    + "when `android:debuggable` is set to `true`.");
                }
            }
        }
    }
}