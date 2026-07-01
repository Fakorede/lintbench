package com.android.tools.lint.checks;

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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {

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
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("android:name");
        if (name == null || name.isEmpty()) {
            return;
        }

        PsiClass cls = context.findClass(name);
        if (cls == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        boolean isMediaBrowserService = evaluator.extendsClass(cls, "android.service.media.MediaBrowserService", false)
                || evaluator.extendsClass(cls, "androidx.media.MediaBrowserServiceCompat", false);

        if (!isMediaBrowserService) {
            return;
        }

        NodeList intentFilters = element.getElementsByTagName("intent-filter");
        boolean hasCorrectFilter = false;

        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttribute("android:name");
                if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                    hasCorrectFilter = true;
                    break;
                }
            }
            if (hasCorrectFilter) {
                break;
            }
        }

        if (!hasCorrectFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService must have an intent-filter with action android.media.browse.MediaBrowserService");
        }
    }
}