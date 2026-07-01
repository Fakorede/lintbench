package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String CERT_SRC_USER = "user";

    private static final String EXPLANATION =
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`<trust-anchors>` inside a `<debug-overrides>` element to make sure they are only "
                    + "available when `android:debuggable` is set to `true`.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    EXPLANANATION,
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No state to initialize.
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String rootTag = stripNamespace(root.getTagName());
        if (!TAG_NETWORK_SECURITY_CONFIG.equals(rootTag)) {
            return;
        }

        NodeList trustAnchors = document.getElementsByTagName(TAG_TRUST_ANCHORS);
        for (int i = 0; i < trustAnchors.getLength(); i++) {
            Element trustAnchor = (Element) trustAnchors.item(i);
            if (!isInsideDebugOverrides(trustAnchor) && containsUserCertificate(trustAnchor)) {
                context.report(
                        ISSUE,
                        context.getLocation(trustAnchor),
                        EXPLANATION);
            }
        }
    }

    private static boolean isInsideDebugOverrides(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            String tag = stripNamespace(((Element) parent).getTagName());
            if (TAG_DEBUG_OVERRIDES.equals(tag)) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private static boolean containsUserCertificate(@NonNull Element trustAnchor) {
        NodeList certificates = trustAnchor.getElementsByTagName(TAG_CERTIFICATES);
        for (int i = 0; i < certificates.getLength(); i++) {
            Element certificate = (Element) certificates.item(i);
            String src = certificate.getAttribute(ATTR_SRC);
            if (CERT_SRC_USER.equals(src)) {
                return true;
            }
        }
        return false;
    }

    private static String stripNamespace(@NonNull String tagName) {
        int colonIndex = tagName.indexOf(':');
        return colonIndex != -1 ? tagName.substring(colonIndex + 1) : tagName;
    }
}