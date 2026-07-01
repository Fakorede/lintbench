package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String REFERENCE_URL =
            "https://developer.android.com/training/auto/audio/index.html#config_manifest";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "android.service.media.MediaBrowserService with an intent-filter for the "
                    + "action android.media.browse.MediaBrowserService to be able to browse "
                    + "and play media.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)),
            REFERENCE_URL);

    private final List<ServiceInfo> mServices = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> mMediaBrowserServices =
            Collections.synchronizedSet(new HashSet<>());

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitElement(@NotNull UElement node, @NotNull JavaContext context) {
        if (node instanceof UClass) {
            UClass cls = (UClass) node;
            String qualifiedName = cls.getQualifiedName();
            if (qualifiedName != null
                    && context.getEvaluator().extendsClass(cls, MEDIA_BROWSER_SERVICE, false)) {
                mMediaBrowserServices.add(qualifiedName);
            }
        }
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(SdkConstants.NS_ANDROID, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        String fqcn = resolveClassName(packageName, name);

        boolean exported =
                "true".equals(
                        element.getAttributeNS(SdkConstants.NS_ANDROID, SdkConstants.ATTR_EXPORTED));

        boolean hasFilter = false;
        NodeList filters = element.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength() && !hasFilter; i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName =
                        action.getAttributeNS(SdkConstants.NS_ANDROID, SdkConstants.ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    hasFilter = true;
                    break;
                }
            }
        }

        mServices.add(new ServiceInfo(fqcn, exported, hasFilter, context.getLocation(element)));
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (ServiceInfo info : mServices) {
            if (info.exported
                    && !info.hasFilter
                    && mMediaBrowserServices.contains(info.className)) {
                context.report(
                        ISSUE,
                        info.location,
                        "Add an intent-filter with action "
                                + MEDIA_BROWSER_SERVICE_ACTION
                                + " to this MediaBrowserService.");
            }
        }
    }

    private static String resolveClassName(String packageName, String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return (packageName != null ? packageName : "") + name;
        }
        if (name.indexOf('.') == -1) {
            return (packageName != null ? packageName + "." : "") + name;
        }
        return name;
    }

    private static final class ServiceInfo {
        final String className;
        final boolean exported;
        final boolean hasFilter;
        final Location location;

        ServiceInfo(
                String className,
                boolean exported,
                boolean hasFilter,
                Location location) {
            this.className = className;
            this.exported = exported;
            this.hasFilter = hasFilter;
            this.location = location;
        }
    }
}