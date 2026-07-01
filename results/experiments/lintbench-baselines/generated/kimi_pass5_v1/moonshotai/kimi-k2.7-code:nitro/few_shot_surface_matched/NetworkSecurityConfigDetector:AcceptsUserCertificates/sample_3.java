package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Accepts User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by "
                            + "your app, which could impact the privacy of your users. Consider nesting "
                            + "your app's `trust-anchors` inside a `<debug-overrides>` element to make "
                            + "sure they are only available when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_XML_SCOPE))
            .setMoreInfo("https://goo.gle/AcceptsUserCertificates");

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Project project) {
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, org.w3c.dom.Element element) {
        if ("certificates".equals(element.getTagName())) {
            String src = element.getAttribute("src");
            if (containsUser(src) && !isInsideDebugOverrides(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Allowing user certificates could allow eavesdroppers to intercept data sent by your app");
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child);
            }
        }
    }

    private static boolean containsUser(String src) {
        if (src == null || src.isEmpty()) {
            return false;
        }
        for (String part : src.split("\\|")) {
            if ("user".equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideDebugOverrides(org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "debug-overrides".equals(((org.w3c.dom.Element) parent).getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}