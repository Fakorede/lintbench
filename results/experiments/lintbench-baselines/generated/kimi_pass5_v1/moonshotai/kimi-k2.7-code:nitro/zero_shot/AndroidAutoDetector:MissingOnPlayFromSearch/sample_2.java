package com.android.tools.lint.checks;

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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UTypeReferenceExpression;
import org.w3c.dom.Attr;

public class AndroidAutoDetector extends Detector
        implements Detector.XmlScanner, Detector.SourceCodeScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_MANIFEST_URI =
            "http://schemas.android.com/apk/res/android";

    private static final String MESSAGE =
            "To support voice searches on Android Auto, override and implement "
                    + "onPlayFromSearch(String query, Bundle bundle)";

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an "
                    + "intent-filter for the action android.media.action.MEDIA_PLAY_FROM_SEARCH, "
                    + "you also need to override and implement "
                    + "onPlayFromSearch(String query, Bundle bundle).",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private boolean mHasPlayFromSearchAction;
    private boolean mHasOnPlayFromSearch;
    private Location mActionLocation;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mHasPlayFromSearchAction = false;
        mHasOnPlayFromSearch = false;
        mActionLocation = null;
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (ANDROID_MANIFEST_URI.equals(attribute.getNamespaceURI())
                && "action".equals(attribute.getOwnerElement().getTagName())
                && ACTION_MEDIA_PLAY_FROM_SEARCH.equals(attribute.getValue())) {
            mHasPlayFromSearchAction = true;
            mActionLocation = context.getLocation(attribute);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if ("onPlayFromSearch".equals(node.getName())
                        && hasCorrectSignature(node)) {
                    mHasOnPlayFromSearch = true;
                }
            }
        };
    }

    private static boolean hasCorrectSignature(@NotNull UMethod node) {
        List<UParameter> parameters = node.getUastParameters();
        if (parameters.size() != 2) {
            return false;
        }
        String first = getTypeName(parameters.get(0));
        String second = getTypeName(parameters.get(1));
        return ("java.lang.String".equals(first) || "String".equals(first))
                && ("android.os.Bundle".equals(second) || "Bundle".equals(second));
    }

    private static String getTypeName(@NotNull UParameter parameter) {
        UTypeReferenceExpression typeRef = parameter.getTypeReference();
        if (typeRef != null) {
            String name = typeRef.getQualifiedName();
            if (name != null) {
                return name;
            }
        }
        return parameter.getType().getCanonicalText();
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (mHasPlayFromSearchAction && !mHasOnPlayFromSearch) {
            context.report(ISSUE, mActionLocation, MESSAGE);
        }
    }
}