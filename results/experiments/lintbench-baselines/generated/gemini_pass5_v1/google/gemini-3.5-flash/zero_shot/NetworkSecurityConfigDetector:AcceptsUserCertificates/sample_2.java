package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data "
                    + "sent by your app, which could impact the privacy of your users. "
                    + "Consider nesting your app's `trust-anchors` inside a `<debug-overrides>` "
                    + "element to make sure they are only available when `android:debuggable` "
                    + "is set to `true`.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/AcceptsUserCertificates")
     .addMoreInfo("https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getNodeName())) {
            return;
        }

        if (!"user".equals(element.getAttribute("src"))) {
            return;
        }

        Node parent = element.getParentNode();
        boolean isInsideDebugOverrides = false;
        while (parent != null) {
            if (parent instanceof Element && "debug-overrides".equals(parent.getNodeName())) {
                isInsideDebugOverrides = true;
                break;
            }
            parent = parent.getParentNode();
        }

        if (!isInsideDebugOverrides) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Accepting user certificates outside of `<debug-overrides>` is a security risk"
            );
        }
    }
}