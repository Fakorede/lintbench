package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            );

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` "
                            + "for the action `android.media.browse.MediaBrowserService` to be able "
                            + "to browse and play media. To do this, add `<intent-filter>` with "
                            + "`<action android:name=\"android.media.browse.MediaBrowserService\" />` "
                            + "to the service that extends `android.service.media.MediaBrowserService`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private final Set<String> mediaBrowserServices = new HashSet<>();
    private final Map<String, Element> manifestServices = new HashMap<>();
    private final Map<String, XmlContext> manifestServiceContexts = new HashMap<>();
    private final Set<String> servicesWithIntentFilter = new HashSet<>();
    private final Map<String, Location> classLocations = new HashMap<>();
    private final Map<String, JavaContext> classContexts = new HashMap<>();

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        mediaBrowserServices.clear();
        manifestServices.clear();
        manifestServiceContexts.clear();
        servicesWithIntentFilter.clear();
        classLocations.clear();
        classContexts.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("service".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if (name == null || name.isEmpty()) {
                return;
            }

            String pkg = context.getProject().getPackage();
            String fqName = getFullyQualifiedName(name, pkg);

            manifestServices.put(fqName, element);
            manifestServiceContexts.put(fqName, context);

            boolean hasFilter = false;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                    NodeList grandChildren = child.getChildNodes();
                    for (int j = 0; j < grandChildren.getLength(); j++) {
                        Node grandChild = grandChildren.item(j);
                        if (grandChild instanceof Element && "action".equals(grandChild.getNodeName())) {
                            Element actionEl = (Element) grandChild;
                            String actionName = actionEl.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if (actionName == null || actionName.isEmpty()) {
                                actionName = actionEl.getAttribute("android:name");
                            }
                            if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                                hasFilter = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (hasFilter) {
                servicesWithIntentFilter.add(fqName);
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqName = declaration.getQualifiedName();
        if (fqName != null) {
            mediaBrowserServices.add(fqName);
            classLocations.put(fqName, context.getNameLocation(declaration));
            classContexts.put(fqName, context);
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // No-op required by specification
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        for (String fqName : mediaBrowserServices) {
            if (!manifestServices.containsKey(fqName)) {
                JavaContext javaContext = classContexts.get(fqName);
                Location location = classLocations.get(fqName);
                if (javaContext != null && location != null) {
                    javaContext.report(
                            ISSUE,
                            location,
                            "Service " + fqName + " should be declared in the manifest with an intent-filter for android.media.browse.MediaBrowserService"
                    );
                }
            } else if (!servicesWithIntentFilter.contains(fqName)) {
                Element element = manifestServices.get(fqName);
                XmlContext xmlContext = manifestServiceContexts.get(fqName);
                if (xmlContext != null && element != null) {
                    xmlContext.report(
                            ISSUE,
                            element,
                            xmlContext.getLocation(element),
                            "Missing intent-filter for android.media.browse.MediaBrowserService"
                    );
                } else {
                    JavaContext javaContext = classContexts.get(fqName);
                    Location location = classLocations.get(fqName);
                    if (javaContext != null && location != null) {
                        javaContext.report(
                                ISSUE,
                                location,
                                "Service " + fqName + " is missing intent-filter for android.media.browse.MediaBrowserService in the manifest"
                        );
                    }
                }
            }
        }
    }

    private String getFullyQualifiedName(String className, String pkg) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        if (className.startsWith(".")) {
            return pkg + className;
        } else if (!className.contains(".")) {
            return pkg + "." + className;
        }
        return className;
    }
}