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
import com.intellij.psi.PsiClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for the action " +
            "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST)
    );

    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String exported = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED);
        if ("false".equals(exported)) {
            return;
        }

        String fqcn = name;
        String pkg = context.getPackageName();
        if (pkg != null) {
            if (name.startsWith(".")) {
                fqcn = pkg + name;
            } else if (!name.contains(".")) {
                fqcn = pkg + "." + name;
            }
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass cls = evaluator.findClass(fqcn);
        if (cls == null || !evaluator.extendsClass(cls, MEDIA_BROWSER_SERVICE_CLASS, true)) {
            return;
        }

        boolean hasRequiredIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if (SdkConstants.TAG_INTENT_FILTER.equals(childEl.getTagName())) {
                    if (hasAction(childEl, MEDIA_BROWSER_SERVICE_ACTION)) {
                        hasRequiredIntentFilter = true;
                        break;
                    }
                }
            }
        }

        if (!hasRequiredIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Service extending MediaBrowserService must have an intent-filter with action " +
                    MEDIA_BROWSER_SERVICE_ACTION);
        }
    }

    private boolean hasAction(Element intentFilter, String actionName) {
        NodeList actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (actionName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}