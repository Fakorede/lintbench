package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                    + "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(Context context, ResourceFile file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No-op
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }
        checkElement(context, root, false);
    }

    private void checkElement(XmlContext context, Element element, boolean insideDebugOverrides) {
        String tagName = element.getTagName();
        boolean isDebug = insideDebugOverrides || "debug-overrides".equals(tagName);

        if ("trust-anchors".equals(tagName) && !isDebug) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element cert = (Element) child;
                    if ("certificates".equals(cert.getTagName()) && "user".equals(cert.getAttribute("src"))) {
                        context.report(ISSUE, cert, context.getLocation(cert),
                                "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                                        + "which could impact the privacy of your users. Consider nesting your app's "
                                        + "trust-anchors inside a <debug-overrides> element to make sure they are only "
                                        + "available when android:debuggable is set to true.");
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, isDebug);
            }
        }
    }
}