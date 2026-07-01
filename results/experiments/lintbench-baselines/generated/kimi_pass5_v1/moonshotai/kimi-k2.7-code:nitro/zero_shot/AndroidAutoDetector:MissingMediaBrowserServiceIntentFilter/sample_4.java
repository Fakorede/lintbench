package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {
    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                    + "for the action `android.media.browse.MediaBrowserService` to be able to "
                    + "browse and play media.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqcn = resolveFullClassName(context, name);
        if (fqcn == null) {
            return;
        }

        JavaEvaluator evaluator = context.getDriver().getJavaEvaluator();
        PsiClass psiClass = evaluator.findClass(fqcn);
        if (psiClass == null) {
            return;
        }

        if (!evaluator.extendsClass(psiClass, MEDIA_BROWSER_SERVICE, false)) {
            return;
        }

        if (!hasRequiredIntentFilter(element)) {
            context.report(
                    MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                    element,
                    context.getLocation(element),
                    "Service extending MediaBrowserService is missing the required "
                            + "intent-filter with action android.media.browse.MediaBrowserService"
            );
        }
    }

    @Nullable
    private static String resolveFullClassName(@NonNull XmlContext context, @NonNull String name) {
        if (name.startsWith(".")) {
            return context.getMainProject().getPackage() + name;
        }
        if (name.indexOf('.') == -1) {
            return context.getMainProject().getPackage() + "." + name;
        }
        return name;
    }

    private static boolean hasRequiredIntentFilter(@NonNull Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (ACTION_MEDIA_BROWSER_SERVICE.equals(action.getAttribute(ATTR_NAME))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}