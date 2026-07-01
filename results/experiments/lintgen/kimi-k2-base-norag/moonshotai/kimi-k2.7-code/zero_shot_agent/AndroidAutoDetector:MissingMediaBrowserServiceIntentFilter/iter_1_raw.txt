package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_EXPORTED;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements XmlScanner, ClassScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_INTERNAL_NAME =
            MEDIA_BROWSER_SERVICE.replace('.', '/');
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
                    + "and play media. See " + REFERENCE_URL,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)));

    private final List<ServiceInfo> mServices = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> mMediaBrowserServices =
            Collections.synchronizedSet(new HashSet<>());

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mServices.clear();
        mMediaBrowserServices.clear();
    }

    @Override
    public void checkByteCode(@NotNull ClassContext context, @NotNull ClassNode classNode) {
        String superName = classNode.superName;
        if (MEDIA_BROWSER_SERVICE_INTERNAL_NAME.equals(superName)) {
            String name = classNode.name;
            if (name != null) {
                mMediaBrowserServices.add(name.replace('/', '.'));
            }
        }
    }

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return null;
    }

    @Override
    public void checkCall(@NotNull ClassContext context, @NotNull ClassNode classNode,
            @NotNull MethodNode method, @NotNull MethodInsnNode call) {
    }

    @Override
    @Nullable
    public List<String> getApplicableFieldNames() {
        return null;
    }

    @Override
    public void checkField(@NotNull ClassContext context, @NotNull ClassNode classNode,
            @NotNull FieldInsnNode field) {
    }

    @Override
    @Nullable
    public int[] getApplicableAsmNodeTypes() {
        return null;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        String fqcn = resolveClassName(packageName, name);

        boolean exported = Boolean.parseBoolean(
                element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED));

        boolean hasFilter = false;
        NodeList filters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength() && !hasFilter; i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
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
            if (info.exported && !info.hasFilter
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