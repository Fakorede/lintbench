package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
            AndroidAutoDetector.class,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for " +
            "the action `android.media.browse.MediaBrowserService` to be able to " +
            "browse and play media.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION
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
        if (pkg != null) {
            if (className.startsWith(".")) {
                className = pkg + className;
            } else if (!className.contains(".")) {
                className = pkg + "." + className;
            }
        }

        UastParser parser = context.getClient().getUastParser(context.getProject());
        JavaEvaluator evaluator = parser.getEvaluator();
        PsiClass psiClass = evaluator.findClass(className);

        if (psiClass != null && evaluator.inheritsFrom(psiClass, "android.service.media.MediaBrowserService", false)) {
            if (!hasMediaBrowserServiceIntentFilter(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing `MediaBrowserService` intent-filter"
                );
            }
        }
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}