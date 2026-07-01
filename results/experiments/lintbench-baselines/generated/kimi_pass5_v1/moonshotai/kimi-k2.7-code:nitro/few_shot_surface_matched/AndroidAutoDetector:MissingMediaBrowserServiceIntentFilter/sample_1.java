package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Context.FileType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService Intent Filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                            + "for the action `android.media.browse.MediaBrowserService`. Without "
                            + "this intent filter the service cannot be browsed or played by "
                            + "Android Auto.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final List<ServiceInfo> mManifestServices = new ArrayList<>();

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull FileType fileType) {
        return fileType == FileType.XML
                || fileType == FileType.JAVA
                || fileType == FileType.KOTLIN
                || fileType == FileType.KOTLIN_SCRIPT;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mManifestServices.clear();
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(
            @NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String className = element.getAttribute(ATTR_NAME);
        if (className == null || className.isEmpty()) {
            return;
        }

        boolean hasAction = false;
        org.w3c.dom.NodeList intentFilters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            org.w3c.dom.Node node = intentFilters.item(i);
            if (!(node instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element intentFilter = (org.w3c.dom.Element) node;
            org.w3c.dom.NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                org.w3c.dom.Node actionNode = actions.item(j);
                if (!(actionNode instanceof org.w3c.dom.Element)) {
                    continue;
                }
                org.w3c.dom.Element action = (org.w3c.dom.Element) actionNode;
                String actionName = action.getAttribute(ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    hasAction = true;
                    break;
                }
            }
            if (hasAction) {
                break;
            }
        }

        mManifestServices.add(new ServiceInfo(className, context, element, hasAction));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        String packageName = context.getMainProject().getPackage();
        for (ServiceInfo info : mManifestServices) {
            if (info.hasAction) {
                continue;
            }
            String resolved = resolveClassName(info.className, packageName);
            if (resolved != null && mMediaBrowserServices.contains(resolved)) {
                info.context.report(
                        ISSUE,
                        info.element,
                        info.context.getLocation(info.element),
                        "Missing MediaBrowserService intent filter. Add an `<intent-filter>` "
                                + "with `<action android:name=\""
                                + MEDIA_BROWSER_SERVICE_ACTION
                                + "\" />` to this service.");
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context, @NonNull UMethod method, @NonNull PsiMethod psiMethod) {
        // No method-level analysis is required for this check.
    }

    private static String resolveClassName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            return packageName != null ? packageName + name : null;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName != null ? packageName + "." + name : null;
    }

    private static class ServiceInfo {
        final String className;
        final XmlContext context;
        final org.w3c.dom.Element element;
        final boolean hasAction;

        ServiceInfo(
                String className,
                XmlContext context,
                org.w3c.dom.Element element,
                boolean hasAction) {
            this.className = className;
            this.context = context;
            this.element = element;
            this.hasAction = hasAction;
        }
    }
}