package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
                    "which could impact the privacy of your users. Consider nesting your app's " +
                    "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
                    "available when `android:debuggable` is set to `true`.",
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
        // No project-wide initialization needed for this detector
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root != null && "network-security-config".equals(root.getTagName())) {
            visitElement(context, root, false);
        }
    }

    private void visitElement(@NonNull XmlContext context, @NonNull Element element, boolean insideDebugOverrides) {
        String tagName = element.getTagName();
        boolean isDebugOverrides = "debug-overrides".equals(tagName);
        boolean currentInsideDebug = insideDebugOverrides || isDebugOverrides;

        if ("trust-anchors".equals(tagName) && !currentInsideDebug) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childEl = (Element) child;
                    if ("certificates".equals(childEl.getTagName())) {
                        String src = childEl.getAttribute("src");
                        if ("user".equals(src)) {
                            context.report(ISSUE, childEl,
                                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
                                    "which could impact the privacy of your users. Consider nesting your app's " +
                                    "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
                                    "available when `android:debuggable` is set to `true`.");
                        }
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child, currentInsideDebug);
            }
        }
    }
}