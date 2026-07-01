package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
                    "`android.service.media.MediaBrowserService` with an `intent-filter` " +
                    "for the action `android.media.browse.MediaBrowserService` to be able to browse " +
                    "and play media.\n\n" +
                    "To do this, add\n" +
                    "`<intent-filter>`\n" +
                    "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n" +
                    "`</intent-filter>`\n" +
                    "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private final List<ServiceInfo> manifestServices = new ArrayList<>();
    private final List<ClassInfo> mediaBrowserServices = new ArrayList<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        manifestServices.clear();
        mediaBrowserServices.clear();
    }

    // XmlScanner implementation
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!SdkConstants.FN_ANDROID_MANIFEST_XML.equals(context.getFile().getName())) {
            return;
        }

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        boolean hasIntentFilter = false;
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element && SdkConstants.TAG_INTENT_FILTER.equals(node.getNodeName())) {
                Element intentFilter = (Element) node;
                NodeList actionNodes = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
                for (int j = 0; j < actionNodes.getLength(); j++) {
                    Element action = (Element) actionNodes.item(j);
                    String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }
            if (hasIntentFilter) {
                break;
            }
        }

        String pkg = context.getProject().getPackage();
        String resolvedName = resolveClassName(name, pkg);
        Location location = context.getNameLocation(element);

        manifestServices.add(new ServiceInfo(resolvedName, hasIntentFilter, location));
    }

    // SourceCodeScanner implementation
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
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        Location location = context.getNameLocation(declaration);
        mediaBrowserServices.add(new ClassInfo(qualifiedName, location));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ClassInfo classInfo : mediaBrowserServices) {
            boolean foundInManifest = false;
            boolean hasFilter = false;
            ServiceInfo matchedService = null;

            for (ServiceInfo serviceInfo : manifestServices) {
                if (serviceInfo.name.equals(classInfo.name)) {
                    foundInManifest = true;
                    matchedService = serviceInfo;
                    if (serviceInfo.hasIntentFilter) {
                        hasFilter = true;
                    }
                    break;
                }
            }

            if (!foundInManifest) {
                context.report(
                        ISSUE,
                        classInfo.location,
                        "Service extending `MediaBrowserService` is not registered in the manifest."
                );
            } else if (!hasFilter) {
                Location location = matchedService.location != null ? matchedService.location : classInfo.location;
                context.report(
                        ISSUE,
                        location,
                        "Missing intent-filter for action `android.media.browse.MediaBrowserService` in service declaration."
                );
            }
        }
    }

    private static String resolveClassName(String name, String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (!name.contains(".")) {
            return pkg + "." + name;
        } else {
            return name;
        }
    }

    private static class ServiceInfo {
        final String name;
        final boolean hasIntentFilter;
        final Location location;

        ServiceInfo(String name, boolean hasIntentFilter, Location location) {
            this.name = name;
            this.hasIntentFilter = hasIntentFilter;
            this.location = location;
        }
    }

    private static class ClassInfo {
        final String name;
        final Location location;

        ClassInfo(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }
}