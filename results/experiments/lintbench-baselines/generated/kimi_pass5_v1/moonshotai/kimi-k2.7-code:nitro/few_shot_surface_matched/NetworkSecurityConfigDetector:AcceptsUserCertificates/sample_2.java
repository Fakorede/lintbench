package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Accepting User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by "
                            + "your app, which could impact the privacy of your users. Consider "
                            + "nesting your app's <trust-anchors> inside a <debug-overrides> "
                            + "element to make sure they are only available when "
                            + "android:debuggable is set to \"true\". "
                            + "See https://developer.android.com/training/articles/security-config#TrustingDebugCa",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_XML_SCOPE));

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }
        checkElement(context, root, false);
    }

    private static void checkElement(
            XmlContext context,
            org.w3c.dom.Element element,
            boolean insideDebugOverrides) {
        String tag = element.getTagName();
        if (TAG_DEBUG_OVERRIDES.equals(tag)) {
            insideDebugOverrides = true;
        } else if (TAG_CERTIFICATES.equals(tag) && !insideDebugOverrides) {
            org.w3c.dom.Attr src = element.getAttributeNode(ATTR_SRC);
            if (src != null && VALUE_USER.equals(src.getValue())) {
                context.report(
                        ISSUE,
                        src,
                        context.getLocation(src),
                        "Allowing user certificates could allow eavesdroppers to intercept data "
                                + "sent by your app, which could impact the privacy of your "
                                + "users. Consider nesting your app's <trust-anchors> inside a "
                                + "<debug-overrides> element to make sure they are only "
                                + "available when android:debuggable is set to \"true\".");
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child, insideDebugOverrides);
            }
        }
    }
}