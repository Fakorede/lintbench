package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService Intent Filter",
                    "An Automotive Media App requires an exported `Service` that extends "
                            + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                            + "for the action `android.media.browse.MediaBrowserService`. "
                            + "Without this intent filter the media app cannot be discovered or "
                            + "used by Android Auto.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest");

    private final Map<String, Location> mediaBrowserServices = new HashMap<>();
    private final List<ServiceInfo> manifestServices = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mediaBrowserServices.clear();
        manifestServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String className = resolveClassName(name, packageName);

        boolean hasFilter = hasMediaBrowserAction(element);

        manifestServices.add(
                new ServiceInfo(className, hasFilter, element, context.getLocation(element)));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String className = declaration.getQualifiedName();
        if (className == null || className.equals(MEDIA_BROWSER_SERVICE)) {
            return;
        }
        mediaBrowserServices.put(className, context.getNameLocation(declaration));
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context, @NonNull UMethod node, @NonNull PsiMethod method) {
        // No method-level checks required for this issue.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mediaBrowserServices.entrySet()) {
            String className = entry.getKey();
            Location classLocation = entry.getValue();
            ServiceInfo match = null;
            for (ServiceInfo info : manifestServices) {
                if (className.equals(info.className)) {
                    match = info;
                    break;
                }
            }

            if (match == null) {
                context.report(
                        ISSUE,
                        classLocation,
                        "MediaBrowserService subclass "
                                + className
                                + " must be declared in AndroidManifest.xml with an <intent-filter> "
                                + "for action "
                                + ACTION_MEDIA_BROWSER_SERVICE);
            } else if (!match.hasFilter) {
                context.report(
                        ISSUE,
                        match.element,
                        match.location,
                        "The service "
                                + className
                                + " must include an <intent-filter> with action "
                                + ACTION_MEDIA_BROWSER_SERVICE);
            }
        }
    }

    private static boolean hasMediaBrowserAction(@NonNull Element service) {
        NodeList filters = service.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                if (!"action".equals(child.getLocalName())) {
                    continue;
                }
                Element action = (Element) child;
                String actionName = action.getAttributeNS(ANDROID_URI, "name");
                if (actionName.isEmpty()) {
                    actionName = action.getAttribute("android:name");
                }
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String resolveClassName(@NonNull String name, @NonNull String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }

    private static class ServiceInfo {
        final String className;
        final boolean hasFilter;
        final Element element;
        final Location location;

        ServiceInfo(
                String className, boolean hasFilter, Element element, Location location) {
            this.className = className;
            this.hasFilter = hasFilter;
            this.element = element;
            this.location = location;
        }
    }
}