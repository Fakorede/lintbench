package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements XmlScanner, Detector.SourceCodeScanner {

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String CLASS_MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent filter",
            "An Automotive Media App requires an exported service that extends "
                    + "android.service.media.MediaBrowserService with an <intent-filter> for the "
                    + "action android.media.browse.MediaBrowserService.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST_FILE))
    );

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final List<ServiceInfo> mServiceInfos = new ArrayList<>();

    private static class ServiceInfo {
        final XmlContext context;
        final Element element;
        final String qualifiedName;
        final boolean hasFilter;

        ServiceInfo(XmlContext context, Element element, String qualifiedName, boolean hasFilter) {
            this.context = context;
            this.element = element;
            this.qualifiedName = qualifiedName;
            this.hasFilter = hasFilter;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mServiceInfos.clear();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String qualifiedName = getQualifiedServiceName(context, element);
        if (qualifiedName == null) {
            return;
        }
        boolean hasFilter = hasMediaBrowserServiceIntentFilter(element);
        mServiceInfos.add(new ServiceInfo(context, element, qualifiedName, hasFilter));
    }

    @Override
    @NonNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NonNull
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }
                if (context.getEvaluator().extendsClass(
                        node.getPsi(), CLASS_MEDIA_BROWSER_SERVICE, false)) {
                    mMediaBrowserServices.add(qualifiedName);
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ServiceInfo info : mServiceInfos) {
            if (mMediaBrowserServices.contains(info.qualifiedName) && !info.hasFilter) {
                info.context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                        info.element,
                        info.context.getLocation(info.element),
                        "Automotive media apps must add an <intent-filter> with "
                                + "<action android:name=\"android.media.browse.MediaBrowserService\"/> "
                                + "to MediaBrowserService services.");
            }
        }
    }

    private static String getQualifiedServiceName(@NonNull XmlContext context, @NonNull Element service) {
        String name = service.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        if (name.startsWith(".")) {
            return context.getMainProject().getPackage() + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return context.getMainProject().getPackage() + "." + name;
    }

    private static boolean hasMediaBrowserServiceIntentFilter(@NonNull Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                continue;
            }
            NodeList actions = child.getChildNodes();
            for (int j = 0, m = actions.getLength(); j < m; j++) {
                Node action = actions.item(j);
                if (action.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                if (!SdkConstants.TAG_ACTION.equals(action.getNodeName())) {
                    continue;
                }
                String actionName = ((Element) action).getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}