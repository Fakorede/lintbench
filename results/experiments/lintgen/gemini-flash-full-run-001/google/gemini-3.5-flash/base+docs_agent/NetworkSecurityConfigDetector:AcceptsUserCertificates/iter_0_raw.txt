package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data " +
            "sent by your app, which could impact the privacy of your users. Consider " +
            "nesting your app's `trust-anchors` inside a `<debug-overrides>` element " +
            "to make sure they are only available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getOwnerDocument() == null || 
            element.getOwnerDocument().getDocumentElement() == null ||
            !"network-security-config".equals(element.getOwnerDocument().getDocumentElement().getTagName())) {
            return;
        }

        String src = element.getAttribute("src");
        if ("user".equals(src)) {
            Node parent = element.getParentNode();
            boolean insideDebugOverrides = false;
            while (parent != null) {
                if (parent instanceof Element) {
                    Element parentElement = (Element) parent;
                    if ("debug-overrides".equals(parentElement.getTagName())) {
                        insideDebugOverrides = true;
                        break;
                    }
                }
                parent = parent.getParentNode();
            }

            if (!insideDebugOverrides) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Accepting user certificates is insecure. Consider nesting your " +
                        "app's `trust-anchors` inside a `<debug-overrides>` element."
                );
            }
        }
    }
}