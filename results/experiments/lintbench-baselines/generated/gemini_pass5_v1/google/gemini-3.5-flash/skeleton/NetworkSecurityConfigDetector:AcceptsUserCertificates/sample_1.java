package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root != null && "network-security-config".equals(root.getTagName())) {
            checkNode(context, root, false);
        }
    }

    private void checkNode(@NonNull XmlContext context, @NonNull org.w3c.dom.Node node, boolean inDebugOverrides) {
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            String tagName = element.getTagName();
            boolean currentDebug = inDebugOverrides || "debug-overrides".equals(tagName);
            if ("certificates".equals(tagName)) {
                String src = element.getAttribute("src");
                if ("user".equals(src) && !currentDebug) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Accepting user certificates in non-debug configuration is a security risk."
                    );
                }
            }
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                checkNode(context, children.item(i), currentDebug);
            }
        }
    }
}