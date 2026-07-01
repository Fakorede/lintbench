package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
            "which could impact the privacy of your users. Consider nesting your app's " +
            "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
            "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String src = element.getAttribute("src");
        if (!"user".equals(src)) {
            return;
        }

        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        Node parent = element.getParentNode();
        boolean insideDebugOverrides = false;
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = ((Element) parent).getTagName();
                if ("debug-overrides".equals(tagName)) {
                    insideDebugOverrides = true;
                    break;
                }
            }
            parent = parent.getParentNode();
        }

        if (!insideDebugOverrides) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Accepting user certificates could allow eavesdroppers to intercept data. " +
                    "Consider nesting this inside `<debug-overrides>`.");
        }
    }
}