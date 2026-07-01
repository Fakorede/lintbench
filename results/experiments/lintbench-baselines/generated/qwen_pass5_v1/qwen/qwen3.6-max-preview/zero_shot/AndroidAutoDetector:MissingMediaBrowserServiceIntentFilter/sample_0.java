package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "MissingMediaBrowserServiceIntentFilter",
        "Missing MediaBrowserService intent-filter",
        "An Automotive Media App requires an exported service that extends `android.service.media.MediaBrowserService` " +
        "with an `intent-filter` for the action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
        "To do this, add\n" +
        "<intent-filter>\n" +
        "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
        "</intent-filter>\n" +
        "to the service that extends `android.service.media.MediaBrowserService`",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String serviceName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (serviceName.isEmpty()) {
            return;
        }

        String pkg = context.getProject().getPackage();
        if (pkg != null) {
            if (serviceName.startsWith(".")) {
                serviceName = pkg + serviceName;
            } else if (serviceName.indexOf('.') == -1) {
                serviceName = pkg + "." + serviceName;
            }
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass psiClass = evaluator.findClass(serviceName);
        if (psiClass == null) {
            return;
        }

        if (!evaluator.extendsClass(psiClass, "android.service.media.MediaBrowserService", false)) {
            return;
        }

        boolean hasRequiredIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node actionNode = filterChildren.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_ACTION.equals(actionNode.getNodeName())) {
                        String actionName = ((Element) actionNode).getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            hasRequiredIntentFilter = true;
                            break;
                        }
                    }
                }
            }
            if (hasRequiredIntentFilter) {
                break;
            }
        }

        if (!hasRequiredIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                "MediaBrowserService must have an intent-filter with action `android.media.browse.MediaBrowserService`");
        }
    }
}