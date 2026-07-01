package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayDeque;
import java.util.Deque;

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

    /** Name of the network security config root element */
    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";

    /** Name of the base-config element */
    private static final String TAG_BASE_CONFIG = "base-config";

    /** Name of the domain-config element */
    private static final String TAG_DOMAIN_CONFIG = "domain-config";

    /** Name of the debug-overrides element */
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";

    /** Name of the trust-anchors element */
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";

    /** Name of the certificates element */
    private static final String TAG_CERTIFICATES = "certificates";

    /** The src attribute */
    private static final String ATTR_SRC = "src";

    /** The user certificates source value */
    private static final String VALUE_USER = "user";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do here; we handle everything in visitDocument
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Only process network-security-config files
        if (!TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }

        // Check base-config and domain-config elements (but NOT debug-overrides)
        checkElement(context, root);
    }

    /**
     * Recursively checks elements for user certificate trust anchors outside of debug-overrides.
     */
    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_DEBUG_OVERRIDES.equals(tagName)) {
                // Skip debug-overrides - user certs are acceptable there
                continue;
            } else if (TAG_BASE_CONFIG.equals(tagName) || TAG_DOMAIN_CONFIG.equals(tagName)) {
                // Recurse into base-config and domain-config
                checkElement(context, childElement);
            } else if (TAG_TRUST_ANCHORS.equals(tagName)) {
                // Check trust-anchors for user certificates
                checkTrustAnchors(context, childElement);
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
            Element childElement = (Element) child;
            if (TAG_CERTIFICATES.equals(childElement.getTagName())) {
                String src = childElement.getAttribute(ATTR_SRC);
                if (VALUE_USER.equals(src)) {
                    // Report the issue on the certificates element
                    Location location = context.getLocation(childElement);
                    context.report(
                            ISSUE,
                            childElement,
                            location,
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