package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                            + "which could impact the privacy of your users. Consider nesting your app's "
                            + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                            + "available when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull com.android.tools.lint.detector.api.Context context) {
        // No project-level initialization required.
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull com.android.tools.lint.detector.api.XmlContext context,
                              @com.android.annotations.NonNull org.w3c.dom.Document document) {
        if (!isNetworkSecurityConfig(context)) {
            return;
        }
        checkCertificates(context, document);
    }

    private boolean isNetworkSecurityConfig(com.android.tools.lint.detector.api.XmlContext context) {
        String name = context.file.getName();
        return "network_security_config.xml".equals(name);
    }

    private void checkCertificates(com.android.tools.lint.detector.api.XmlContext context, org.w3c.dom.Node node) {
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            if ("certificates".equals(element.getTagName())) {
                String src = element.getAttribute("src");
                if (src != null && "user".equals(src) && !isInsideDebugOverrides(element)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "`<certificates src=\"user\" />` allows user certificates, which may allow "
                                    + "eavesdroppers to intercept data sent by your app. Consider moving the "
                                    + "containing `<trust-anchors>` into `<debug-overrides>` so it is only "
                                    + "available in debug builds.");
                }
            }
        }

        for (org.w3c.dom.Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkCertificates(context, child);
        }
    }

    private boolean isInsideDebugOverrides(org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
            if ("debug-overrides".equals(parentElement.getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}