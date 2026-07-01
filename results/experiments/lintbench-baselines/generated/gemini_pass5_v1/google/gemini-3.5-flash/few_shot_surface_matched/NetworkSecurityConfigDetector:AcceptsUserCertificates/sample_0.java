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
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                            + "which could impact the privacy of your users. Consider nesting your app's "
                            + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                            + "available when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No-op, but overridden as required by specification
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("network-security-config")) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, org.w3c.dom.Element element) {
        if (element.getTagName().equals("certificates")) {
            String src = element.getAttribute("src");
            if ("user".equals(src)) {
                if (!isUnderDebugOverrides(element)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "App accepts user certificates, which can be vulnerable to MITM attacks. "
                                    + "Consider moving these certificates inside a `<debug-overrides>` element.");
                }
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element) {
                checkElement(context, (org.w3c.dom.Element) child);
            }
        }
    }

    private boolean isUnderDebugOverrides(org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent instanceof org.w3c.dom.Element) {
            if (((org.w3c.dom.Element) parent).getTagName().equals("debug-overrides")) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}