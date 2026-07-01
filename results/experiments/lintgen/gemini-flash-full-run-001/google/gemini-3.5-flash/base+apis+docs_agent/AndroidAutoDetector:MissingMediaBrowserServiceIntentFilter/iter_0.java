package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for the " +
            "action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
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
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
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
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        List<File> manifestFiles = context.getProject().getManifestFiles();
        if (manifestFiles.isEmpty()) {
            return;
        }

        boolean foundService = false;
        boolean hasFilter = false;

        for (File manifestFile : manifestFiles) {
            Document document = context.getClient().getXmlDocument(manifestFile);
            if (document == null) {
                continue;
            }
            NodeList services = document.getElementsByTagName("service");
            for (int i = 0; i < services.getLength(); i++) {
                Element serviceElement = (Element) services.item(i);
                String serviceName = getAndroidAttribute(serviceElement, "name");
                String resolvedName = resolveClassName(context, document, serviceName);
                if (className.equals(resolvedName)) {
                    foundService = true;
                    if (hasMediaBrowserServiceIntentFilter(serviceElement)) {
                        hasFilter = true;
                        break;
                    }
                }
            }
            if (hasFilter) {
                break;
            }
        }

        if (foundService && !hasFilter) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Service extends `MediaBrowserService` but does not declare the required intent-filter for `android.media.browse.MediaBrowserService` in the manifest."
            );
        }
    }

    private String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }

    private String resolveClassName(JavaContext context, Document document, String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            return null;
        }
        if (serviceName.startsWith(".")) {
            String packageName = getPackageName(context, document);
            return packageName + serviceName;
        } else if (!serviceName.contains(".")) {
            String packageName = getPackageName(context, document);
            return packageName + "." + serviceName;
        }
        return serviceName;
    }

    private String getPackageName(JavaContext context, Document document) {
        String packageName = document.getDocumentElement().getAttribute("package");
        if (packageName != null && !packageName.isEmpty()) {
            return packageName;
        }
        packageName = context.getProject().getPackage();
        if (packageName != null && !packageName.isEmpty()) {
            return packageName;
        }
        return "";
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList intentFilters = serviceElement.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = getAndroidAttribute(action, "name");
                if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}