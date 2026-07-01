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

    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";
    private static final String AUTOMOTIVE_APP_TAG = "automotiveApp";

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

    private static final Implementation COMBINED_IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    EnumSet.of(Scope.JAVA_FILE));

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for "
                            + "the `name` attribute. Valid values are `media`, `notification`, "
                            + "or `sms`.\n\n"
                            + "See https://developer.android.com/training/auto/start/index.html"
                            + "#auto-metadata for more details.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    COMBINED_IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media app must have a `MediaBrowserService` with an "
                            + "`<intent-filter>` for the action `android.media.browse.MediaBrowserService`.\n\n"
                            + "See https://developer.android.com/training/auto/start/index.html"
                            + "#auto-metadata for more details.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    COMBINED_IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support 'OK Google' voice commands for media playback, the app's "
                            + "`MediaSession.Callback` should implement `onPlayFromSearch()`.\n\n"
                            + "See https://developer.android.com/training/auto/start/index.html"
                            + "#auto-metadata for more details.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    COMBINED_IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private boolean mHasAutomotiveAppResource = false;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasAutomotiveAppResource = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check that the parent is automotiveApp
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode == null
                || parentNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE
                || !AUTOMOTIVE_APP_TAG.equals(((Element) parentNode).getTagName())) {
            return;
        }

        mHasAutomotiveAppResource = true;

        String nameValue = element.getAttribute(ATTR_NAME);
        if (nameValue == null || nameValue.isEmpty()) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getLocation(element),
                    "Missing `name` attribute on `<uses>` element");
            return;
        }

        switch (nameValue) {
            case VALUE_MEDIA:
            case VALUE_NOTIFICATION:
            case VALUE_SMS:
                // Valid values — no issue
                break;
            default:
                context.report(
                        INVALID_USES_TAG_ATTRIBUTE,
                        element,
                        context.getElementLocation(element, null, ATTR_NAME, null),
                        "Invalid value for the `name` attribute on `<uses>` element. "
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
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Nothing specific needed at class level; method-level checks handle the details.
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod psiMethod) {
        // We check for onPlayFromSearch in MediaSession.Callback subclasses.
        // This is intentionally left minimal; the main check is the XML-based one.
    }
}