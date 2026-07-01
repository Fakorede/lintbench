package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    private static final String NETWORK_SECURITY_CONFIG_FILE = "network_security_config.xml";

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                    + "available when `android:debuggable` is set to `true`.\n\n"
                    + "Refer to:\n"
                    + "  - https://goo.gle/AcceptsUserCertificates\n"
                    + "  - https://developer.android.com/training/articles/security-config#TrustingDebugCa",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!NETWORK_SECURITY_CONFIG_FILE.equals(context.file.getName())) {
            return;
        }

        if ("user".equals(element.getAttribute("src")) && !isInsideDebugOverrides(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app");
        }
    }

    private static boolean isInsideDebugOverrides(@NotNull Element element) {
        Node current = element.getParentNode();
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element parent = (Element) current;
            if ("debug-overrides".equals(parent.getTagName())) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }
}