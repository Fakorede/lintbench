package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Accepting User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                            + "which could impact the privacy of your users. Consider nesting your app's "
                            + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are "
                            + "only available when `android:debuggable` is set to `true`.",
                    "https://goo.gle/AcceptsUserCertificates",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.NETWORK_SECURITY_CONFIG_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"user".equals(element.getAttribute("src"))) {
            return;
        }

        if (isUnderDebugOverrides(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                        + "which could impact the privacy of your users. Consider nesting your app's "
                        + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are "
                        + "only available when `android:debuggable` is set to `true`.");
    }

    private static boolean isUnderDebugOverrides(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if ("debug-overrides".equals(parent.getNodeName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}