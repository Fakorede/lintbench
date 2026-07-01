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

    private static final String AUTOMOTIVE_APP_TAG = "automotiveApp";
    private static final String USES_TAG = "uses";
    private static final String ATTR_NAME = "name";

    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";

    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String NOTIFICATION_LISTENER_SERVICE =
            "android.service.notification.NotificationListenerService";

    private static final Implementation XML_IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE));

    private static final Implementation JAVA_IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE);

    private static final Implementation COMBINED_IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE));

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for "
                            + "the `name` attribute. Valid values are `media`, `notification`, "
                            + "or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive app that specifies `media` in the `<uses>` element of its "
                            + "automotive metadata file must have a `Service` that handles the "
                            + "`android.media.browse.MediaBrowserService` intent.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding the "
                            + "`android.intent.action.MEDIA_PLAY_FROM_SEARCH` intent-filter for "
                            + "the `MediaBrowserService`, the app's `MediaSession.Callback` "
                            + "should also implement `onPlayFromSearch`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    private boolean mUsesMedia;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_TAG);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mUsesMedia = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (!USES_TAG.equals(tagName)) {
            return;
        }

        // Check that the parent is automotiveApp
        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !AUTOMOTIVE_APP_TAG.equals(parent.getNodeName())) {
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
                mUsesMedia = true;
                break;
            case VALUE_NOTIFICATION:
            case VALUE_SMS:
                break;
            default:
                context.report(
                        INVALID_USES_TAG_ATTRIBUTE,
                        element,
                        context.getElementLocation(element, null, ATTR_NAME, null),
                        "Invalid value for `name` attribute in `<uses>` element. "
                                + "Valid values are `media`, `notification`, or `sms`.");
                break;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_BROWSER_SERVICE_COMPAT,
                MEDIA_BROWSER_SERVICE,
                "android.media.session.MediaSession",
                "android.support.v4.media.session.MediaSessionCompat");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this is a MediaBrowserService subclass
        if (extendsMediaBrowserService(context, declaration)) {
            // We note that a MediaBrowserService exists; detailed checks
            // about intent filters are manifest-level.
        }
    }

    private boolean extendsMediaBrowserService(
            @NonNull JavaContext context, @NonNull UClass declaration) {
        return context.getEvaluator().extendsClass(
                declaration.getJavaPsi(), MEDIA_BROWSER_SERVICE_COMPAT, true)
                || context.getEvaluator().extendsClass(
                        declaration.getJavaPsi(), MEDIA_BROWSER_SERVICE, true);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod psiMethod) {
        // No-op for now; override present to satisfy interface requirements
        // and allow future extension for onPlayFromSearch checks.
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }
}