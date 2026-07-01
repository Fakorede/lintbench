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
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    private static final String USER_CERTIFICATES_MESSAGE =
            "Accepting user certificates could allow eavesdroppers to intercept data sent by your app. "
                    + "Consider nesting your app's trust-anchors inside a `<debug-overrides>` element "
                    + "so they are only available when android:debuggable is set to true.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your "
                            + "app, which could impact the privacy of your users. Consider nesting your "
                            + "app's trust-anchors inside a `<debug-overrides>` element to make sure they "
                            + "are only available when android:debuggable is set to true.",
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
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }

        NodeList certificates = document.getElementsByTagName(TAG_CERTIFICATES);
        for (int i = 0; i < certificates.getLength(); i++) {
            Node node = certificates.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element certificate = (Element) node;
            if (VALUE_USER.equals(certificate.getAttribute(ATTR_SRC))
                    && !isWithinDebugOverrides(certificate)) {
                context.report(ISSUE, certificate, context.getLocation(certificate),
                        USER_CERTIFICATES_MESSAGE);
            }
        }
    }

    private static boolean isWithinDebugOverrides(Element element) {
        Node node = element.getParentNode();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element parent = (Element) node;
                if (TAG_DEBUG_OVERRIDES.equals(parent.getTagName())) {
                    return true;
                }
            }
            node = node.getParentNode();
        }
        return false;
    }
}