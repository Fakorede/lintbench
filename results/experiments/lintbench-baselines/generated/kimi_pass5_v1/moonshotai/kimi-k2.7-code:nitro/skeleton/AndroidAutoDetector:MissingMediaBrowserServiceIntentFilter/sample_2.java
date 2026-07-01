package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector
        implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_EXPORTED = "exported";
    private static final String CLASS_MEDIABROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String ACTION_MEDIABROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive media app must declare an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                            + "containing the action `android.media.browse.MediaBrowserService`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final List<ServiceInfo> mServices = new ArrayList<>();

    private static class ServiceInfo {
        final String className;
        final Element element;
        final XmlContext context;
        final boolean hasFilter;

        ServiceInfo(
                @NonNull String className,
                @NonNull Element element,
                @NonNull XmlContext context,
                boolean hasFilter) {
            this.className = className;
            this.element = element;
            this.context = context;
            this.hasFilter = hasFilter;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mServices.clear();
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_MEDIABROWSER_SERVICE);
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
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Not needed for this check.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String exported = element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED);
        if ("false".equals(exported)) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String className = getFullyQualifiedServiceName(packageName, name);
        if (className == null) {
            return;
        }

        boolean hasFilter = hasMediaBrowserAction(element);
        mServices.add(new ServiceInfo(className, element, context, hasFilter));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (ServiceInfo info : mServices) {
            if (!mMediaBrowserServices.contains(info.className)) {
                continue;
            }
            if (info.hasFilter) {
                continue;
            }
            info.context.report(
                    ISSUE,
                    info.element,
                    info.context.getLocation(info.element),
                    "Missing intent-filter action "
                            + ACTION_MEDIABROWSER_SERVICE
                            + " for the MediaBrowserService `"
                            + info.className
                            + "`."
            );
        }

        mMediaBrowserServices.clear();
        mServices.clear();
    }

    @Nullable
    private static String getFullyQualifiedServiceName(
            @Nullable String packageName, @NonNull String name) {
        if (name.isEmpty()) {
            return null;
        }
        if (name.startsWith(".")) {
            if (packageName == null || packageName.isEmpty()) {
                return null;
            }
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        return packageName + "." + name;
    }

    private static boolean hasMediaBrowserAction(@NonNull Element service) {
        NodeList filters = service.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MEDIABROWSER_SERVICE.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}