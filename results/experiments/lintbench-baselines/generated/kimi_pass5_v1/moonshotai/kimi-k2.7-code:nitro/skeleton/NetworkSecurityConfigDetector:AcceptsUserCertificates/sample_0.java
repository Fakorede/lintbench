package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    private static final String MESSAGE =
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, which could impact the privacy of your users. Consider nesting your app's `<trust-anchors>` inside a `<debug-overrides>` element to make sure they are only available when `android:debuggable` is set to `true`.";

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    MESSAGE,
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No project-level state needs to be initialized for this detector.
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }
        checkCertificates(context, root, false);
    }

    private void checkCertificates(XmlContext context, org.w3c.dom.Element element, boolean debugOnly) {
        String tag = element.getTagName();
        if ("debug-overrides".equals(tag)) {
            debugOnly = true;
        } else if ("certificates".equals(tag)) {
            String src = element.getAttribute("src");
            if (!debugOnly && "user".equals(src.trim())) {
                context.report(ISSUE, element, context.getLocation(element), MESSAGE);
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkCertificates(context, (org.w3c.dom.Element) child, debugOnly);
            }
        }
    }
}