package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                    + "for the action `android.media.browse.MediaBrowserService` to be able to "
                    + "browse and play media. Add an `<intent-filter>` containing "
                    + "`<action android:name=\"android.media.browse.MediaBrowserService\" />` "
                    + "to the service that extends `android.service.media.MediaBrowserService`.",
            "https://developer.android.com/training/auto/audio/index.html#config_manifest",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        String fqcn = getQualifiedName(context, serviceName);
        if (fqcn == null) {
            return;
        }

        JavaEvaluator evaluator = context.getDriver().getJavaEvaluator();
        PsiClass psiClass = evaluator.findClass(fqcn);
        if (psiClass == null || !evaluator.extendsClass(psiClass, MEDIA_BROWSER_SERVICE, false)) {
            return;
        }

        if (hasMediaBrowserServiceAction(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Service extends MediaBrowserService but is missing the "
                        + "android.media.browse.MediaBrowserService intent-filter");
    }

    private static boolean hasMediaBrowserServiceAction(Element service) {
        NodeList filters = service.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getQualifiedName(XmlContext context, String className) {
        if (className.isEmpty()) {
            return null;
        }

        String packageName = context.getDocument().getDocumentElement()
                .getAttribute(SdkConstants.ATTR_PACKAGE);

        if (className.startsWith(".")) {
            return packageName + className;
        }

        if (className.contains(".")) {
            return className;
        }

        if (packageName == null || packageName.isEmpty()) {
            return null;
        }

        return packageName + "." + className;
    }
}