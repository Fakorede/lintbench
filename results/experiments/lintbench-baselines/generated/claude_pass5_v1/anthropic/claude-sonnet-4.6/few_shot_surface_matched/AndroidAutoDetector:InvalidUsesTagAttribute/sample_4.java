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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";

    private static final String MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession.Callback";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String NOTIFICATION_LISTENER_SERVICE =
            "android.service.notification.NotificationListenerService";

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
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`MediaBrowserService` with an `intent-filter` for the action "
                            + "`android.media.browse.MediaBrowserService`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding the "
                            + "`android.intent.action.MEDIA_PLAY_FROM_SEARCH` intent-filter, "
                            + "you also need to override the `onPlayFromSearch(String query, "
                            + "Bundle extras)` method of your `MediaSession.Callback`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull com.android.tools.lint.detector.api.Project project) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize before checking the root project
    }

    // ---- XmlScanner ----

    @Override
    @Nullable
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (!TAG_USES.equals(tagName)) {
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

        if (!VALUE_MEDIA.equals(nameValue)
                && !VALUE_NOTIFICATION.equals(nameValue)
                && !VALUE_SMS.equals(nameValue)) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Invalid attribute value `%1$s` for `name`. "
                                    + "Valid values are `media`, `notification`, or `sms`.",
                            nameValue));
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_CALLBACK,
                MEDIA_BROWSER_SERVICE_COMPAT,
                MEDIA_BROWSER_SERVICE,
                NOTIFICATION_LISTENER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if the class extends MediaSession.Callback and implements onPlayFromSearch
        boolean extendsMediaSessionCallback = false;
        com.intellij.psi.PsiClass superClass = declaration.getSuperClass();
        while (superClass != null) {
            String qualifiedName = superClass.getQualifiedName();
            if (MEDIA_SESSION_CALLBACK.equals(qualifiedName)) {
                extendsMediaSessionCallback = true;
                break;
            }
            superClass = superClass.getSuperClass();
        }

        if (extendsMediaSessionCallback) {
            boolean hasOnPlayFromSearch = false;
            for (PsiMethod method : declaration.getMethods()) {
                if ("onPlayFromSearch".equals(method.getName())) {
                    hasOnPlayFromSearch = true;
                    break;
                }
            }
            if (!hasOnPlayFromSearch) {
                context.report(
                        MISSING_ON_PLAY_FROM_SEARCH,
                        declaration,
                        context.getNameLocation(declaration),
                        "This class extends `MediaSession.Callback` but does not override "
                                + "`onPlayFromSearch`. If you want to support voice searches on "
                                + "Android Auto, you should override `onPlayFromSearch`.");
            }
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Additional method-level checks can be added here if needed
    }
}