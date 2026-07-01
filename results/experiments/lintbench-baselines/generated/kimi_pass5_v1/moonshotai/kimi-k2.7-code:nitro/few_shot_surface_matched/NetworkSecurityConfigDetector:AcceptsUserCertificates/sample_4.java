package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Accepting User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent"
                            + " by your app, which could impact the privacy of your users."
                            + " Consider nesting your app's `trust-anchors` inside a"
                            + " `<debug-overrides>` element to make sure they are only available"
                            + " when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

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
    public void visitDocument(XmlContext context) {
        org.w3c.dom.Document document = context.getDocument();
        if (document == null) {
            return;
        }
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        if (!TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }
        checkElement(context, root, false);
    }

    private void checkElement(
            XmlContext context, org.w3c.dom.Element element, boolean inDebugOverrides) {
        String tagName = element.getTagName();
        boolean insideDebug = inDebugOverrides || TAG_DEBUG_OVERRIDES.equals(tagName);

        if (TAG_CERTIFICATES.equals(tagName)
                && !insideDebug
                && VALUE_USER.equals(element.getAttribute(ATTR_SRC))) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Allowing user certificates could allow eavesdroppers to intercept data sent"
                            + " by your app, which could impact the privacy of your users."
                            + " Consider nesting your app's `trust-anchors` inside a"
                            + " `<debug-overrides>` element to make sure they are only available"
                            + " when `android:debuggable` is set to `true`.");
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child, insideDebug);
            }
        }
    }
}