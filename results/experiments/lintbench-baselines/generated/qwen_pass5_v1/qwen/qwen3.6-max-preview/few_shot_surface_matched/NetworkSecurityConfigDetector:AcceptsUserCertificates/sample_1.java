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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global initialization required
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }
        traverse(context, root, false);
    }

    private void traverse(@NonNull XmlContext context, @NonNull Element element, boolean insideDebugOverrides) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String tagName = child.getTagName();
                boolean isDebugOverrides = "debug-overrides".equals(tagName);
                boolean currentInsideDebug = insideDebugOverrides || isDebugOverrides;

                if ("certificates".equals(tagName)) {
                    String src = child.getAttribute("src");
                    if ("user".equals(src) && !currentInsideDebug) {
                        context.report(
                                ISSUE,
                                child,
                                context.getLocation(child),
                                "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                                        + "which could impact the privacy of your users. Consider nesting your app's "
                                        + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                                        + "available when `android:debuggable` is set to `true`.");
                    }
                }
                traverse(context, child, currentInsideDebug);
            }
        }
    }
}