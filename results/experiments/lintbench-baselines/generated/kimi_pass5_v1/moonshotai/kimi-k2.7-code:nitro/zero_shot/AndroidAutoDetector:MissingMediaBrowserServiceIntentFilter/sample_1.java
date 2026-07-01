package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidAutoDetector extends Detector
        implements XmlScanner, Detector.SourceCodeScanner {

    public static final String ISSUE_ID = "MissingMediaBrowserServiceIntentFilter";
    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String ISSUE_SUMMARY =
            "Missing MediaBrowserService intent-filter";
    private static final String ISSUE_EXPLANATION =
            "An Automotive Media App requires an exported service that extends "
                    + "android.service.media.MediaBrowserService with an intent-filter "
                    + "for the action android.media.browse.MediaBrowserService. To fix this, "
                    + "add an <intent-filter> containing that <action> to the service.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_SUMMARY,
            ISSUE_EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE)
    );

    private final List<String> mMediaBrowserServices = new ArrayList<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mManifestServices.clear();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String serviceClass : mMediaBrowserServices) {
            ServiceInfo info = mManifestServices.get(serviceClass);
            if (info == null) {
                continue;
            }
            if (!hasMediaBrowserServiceIntentFilter(info.element)) {
                info.context.report(
                        ISSUE,
                        info.element,
                        info.context.getLocation(info.element),
                        "The service must declare an intent-filter with action "
                                + MEDIA_BROWSER_SERVICE_ACTION);
            }
        }
        mMediaBrowserServices.clear();
        mManifestServices.clear();
    }

    @Override
    @NonNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName);
        }
    }

    @Override
    @NonNull
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = getQualifiedServiceName(context, element);
        if (serviceName != null) {
            mManifestServices.put(serviceName, new ServiceInfo(context, element));
        }
    }

    private static boolean hasMediaBrowserServiceIntentFilter(@NonNull Element service) {
        NodeList filters = service.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getQualifiedServiceName(@NonNull XmlContext context,
            @NonNull Element service) {
        String name = service.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return null;
        }

        Element manifest = context.document.getDocumentElement();
        String packageName = manifest.getAttribute(ATTR_PACKAGE);
        if (packageName == null || packageName.isEmpty()) {
            return name;
        }

        if (name.startsWith(".")) {
            return packageName + name;
        }

        if (name.indexOf('.') < 0) {
            return packageName + "." + name;
        }

        return name;
    }

    private static class ServiceInfo {
        final XmlContext context;
        final Element element;

        ServiceInfo(@NonNull XmlContext context, @NonNull Element element) {
            this.context = context;
            this.element = element;
        }
    }
}