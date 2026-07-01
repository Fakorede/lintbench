package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
            "An Automotive Media App must expose an exported service that extends " +
            "`android.service.media.MediaBrowserService`, and that service must declare an " +
            "`<intent-filter>` with the action `android.media.browse.MediaBrowserService`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqcn = resolveServiceName(context, name);
        JavaEvaluator evaluator = context.getDriver().getJavaEvaluator();
        PsiClass psiClass = evaluator.findClass(fqcn);
        if (psiClass == null || !evaluator.extendsClass(psiClass, MEDIA_BROWSER_SERVICE, false)) {
            return;
        }

        if (!hasMediaBrowserAction(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "MediaBrowserService is missing an intent-filter for action " +
                    "`android.media.browse.MediaBrowserService`.");
        }
    }

    private static boolean hasMediaBrowserAction(@NonNull Element service) {
        NodeList intentFilters = service.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @NonNull
    private static String resolveServiceName(@NonNull XmlContext context, @NonNull String name) {
        String packageName = context.getProject().getPackage();
        if (packageName == null || packageName.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.indexOf('.') >= 0) {
            return name;
        }
        return packageName + "." + name;
    }
}