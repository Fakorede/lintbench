package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";

    private static final String MEDIA_SESSION_COMPAT =
            "android.support.v4.media.session.MediaSessionCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String NOTIFICATION_LISTENER_SERVICE =
            "android.service.notification.NotificationListenerService";

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for"
                            + " the `name` attribute. Valid values are `media`, `notification`,"
                            + " or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent filter",
                    "An Automotive Media app must have a `Service` that extends"
                            + " `MediaBrowserService` and that is exported with an intent filter"
                            + " for `android.media.browse.MediaBrowserService`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing intent filter for media search",
                    "An Automotive Media app should handle the"
                            + " `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch` implementation",
                    "An Automotive Media app that handles"
                            + " `android.media.action.MEDIA_PLAY_FROM_SEARCH` should also"
                            + " override `onPlayFromSearch` in its `MediaSession.Callback`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    private boolean mIsAutomotiveApp = false;
    private boolean mIsMediaApp = false;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIsAutomotiveApp = false;
        mIsMediaApp = false;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only care about <uses> elements inside <automotiveApp>
        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !TAG_AUTOMOTIVE_APP.equals(parent.getNodeName())) {
            return;
        }

        String nameValue = element.getAttribute(ATTR_NAME);
        if (nameValue == null || nameValue.isEmpty()) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getLocation(element),
                    "Missing `name` attribute in `<uses>` element");
            return;
        }

        switch (nameValue) {
            case VALUE_MEDIA:
                mIsAutomotiveApp = true;
                mIsMediaApp = true;
                break;
            case VALUE_NOTIFICATION:
            case VALUE_SMS:
                mIsAutomotiveApp = true;
                break;
            default:
                context.report(
                        INVALID_USES_TAG_ATTRIBUTE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Invalid value for `name` attribute in `<uses>` element."
                                        + " Valid values are `media`, `notification`, or `sms`,"
                                        + " but was `%1$s`",
                                nameValue));
                break;
        }
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_COMPAT, MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mIsMediaApp) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Check MediaSessionCompat.Callback subclasses for onPlayFromSearch
        JavaContext.SuperclassIterator iterator =
                new JavaContext.SuperclassIterator(context, declaration);
        // We'll check methods directly on the class
        boolean foundOnPlayFromSearch = false;
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                foundOnPlayFromSearch = true;
                break;
            }
        }

        if (!foundOnPlayFromSearch
                && context.getEvaluator().extendsClass(declaration, MEDIA_SESSION_COMPAT + ".Callback", false)) {
            context.report(
                    MISSING_ON_PLAY_FROM_SEARCH,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class does not override `onPlayFromSearch` from"
                            + " `MediaSession.Callback`; an Automotive Media app should"
                            + " handle the `android.media.action.MEDIA_PLAY_FROM_SEARCH`"
                            + " intent");
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Intentionally empty — presence of onPlayFromSearch is checked in visitClass
    }
}