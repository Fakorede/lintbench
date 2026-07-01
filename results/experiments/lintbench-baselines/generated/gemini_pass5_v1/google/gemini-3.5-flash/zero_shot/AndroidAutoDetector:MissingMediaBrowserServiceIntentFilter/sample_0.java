package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `intent-filter` for the "
                    + "action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n"
                    + "To do this, add\n"
                    + "```xml\n"
                    + "<intent-filter>\n"
                    + "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n"
                    + "</intent-filter>\n"
                    + "```\n"
                    + "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isAbstract(declaration)) {
            return;
        }

        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        Document mergedManifest = context.getProject().getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        Element manifestElement = mergedManifest.getDocumentElement();
        if (manifestElement == null) {
            return;
        }
        String pkg = manifestElement.getAttribute("package");

        NodeList services = mergedManifest.getElementsByTagName("service");
        boolean serviceFound = false;
        boolean hasCorrectFilter = false;

        for (int i = 0; i < services.getLength(); i++) {
            Element serviceElement = (Element) services.item(i);
            String serviceName = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            String fqName = resolveClassName(serviceName, pkg);
            if (className.equals(fqName)) {
                serviceFound = true;
                if (hasMediaBrowserIntentFilter(serviceElement)) {
                    hasCorrectFilter = true;
                    break;
                }
            }
        }

        if (serviceFound && !hasCorrectFilter) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This service extends `MediaBrowserService` but is missing the required "
                            + "`android.media.browse.MediaBrowserService` intent-filter in the manifest."
            );
        }
    }

    private static String resolveClassName(String serviceName, String pkg) {
        if (serviceName == null || serviceName.isEmpty()) {
            return "";
        }
        if (serviceName.startsWith(".")) {
            return pkg + serviceName;
        } else if (!serviceName.contains(".")) {
            return pkg + "." + serviceName;
        }
        return serviceName;
    }

    private static boolean hasMediaBrowserIntentFilter(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}