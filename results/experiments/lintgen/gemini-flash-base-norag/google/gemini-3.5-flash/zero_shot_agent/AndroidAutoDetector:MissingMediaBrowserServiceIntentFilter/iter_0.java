package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.SdkConstants;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.intellij.psi.PsiClass;
import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for the " +
            "action `android.media.browse.MediaBrowserService` to be able to browse and play media.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String className = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (className == null || className.isEmpty()) {
            return;
        }

        String pkg = context.getProject().getPackage();
        if (pkg == null || pkg.isEmpty()) {
            pkg = element.getOwnerDocument().getDocumentElement().getAttribute(SdkConstants.ATTR_PACKAGE);
        }

        String fqName = className;
        if (pkg != null && !pkg.isEmpty()) {
            if (className.startsWith(".")) {
                fqName = pkg + className;
            } else if (!className.contains(".")) {
                fqName = pkg + "." + className;
            }
        }

        PsiClass psiClass = context.getEvaluator().findClass(fqName);
        if (psiClass == null) {
            return;
        }

        boolean isMediaBrowserService = context.getEvaluator().inheritsFrom(psiClass, "android.service.media.MediaBrowserService", false)
                || context.getEvaluator().inheritsFrom(psiClass, "androidx.media.MediaBrowserServiceCompat", false);

        if (isMediaBrowserService) {
            boolean hasIntentFilter = false;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                    NodeList filterChildren = child.getChildNodes();
                    for (int j = 0; j < filterChildren.getLength(); j++) {
                        Node filterChild = filterChildren.item(j);
                        if (filterChild instanceof Element && "action".equals(filterChild.getNodeName())) {
                            Element action = (Element) filterChild;
                            String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                            if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                                hasIntentFilter = true;
                                break;
                            }
                        }
                    }
                }
                if (hasIntentFilter) {
                    break;
                }
            }

            if (!hasIntentFilter) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Service extends `MediaBrowserService` but does not declare an intent-filter for `android.media.browse.MediaBrowserService`"
                );
            }
        }
    }
}