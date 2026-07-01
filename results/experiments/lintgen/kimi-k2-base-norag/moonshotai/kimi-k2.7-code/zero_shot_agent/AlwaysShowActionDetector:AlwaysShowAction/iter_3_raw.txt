package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";
    private static final String FIELD_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String FIELD_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    private final Map<File, Integer> mAlwaysCounts = new HashMap<>();
    private final Map<File, Integer> mIfRoomCounts = new HashMap<>();
    private final Map<File, Location> mFirstAlwaysLocations = new HashMap<>();

    private boolean mJavaAlwaysSeen;
    private boolean mJavaIfRoomSeen;
    private Location mJavaAlwaysLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@") || value.startsWith("?")) {
            return;
        }

        File file = context.file;
        for (String token : value.split("\\|")) {
            String trimmed = token.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                mAlwaysCounts.put(file, mAlwaysCounts.getOrDefault(file, 0) + 1);
                mFirstAlwaysLocations.putIfAbsent(file, context.getLocation(attribute));
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                mIfRoomCounts.put(file, mIfRoomCounts.getOrDefault(file, 0) + 1);
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        File file = xmlContext.file;
        int alwaysCount = mAlwaysCounts.getOrDefault(file, 0);
        int ifRoomCount = mIfRoomCounts.getOrDefault(file, 0);

        if (alwaysCount > 2 || (alwaysCount > 0 && ifRoomCount == 0)) {
            String message = "Avoid using `showAsAction=\"always\"`; use `showAsAction=\"ifRoom\"` instead";
            Location location = mFirstAlwaysLocations.get(file);
            if (location != null) {
                xmlContext.report(ISSUE, location, message);
            }
        }

        mAlwaysCounts.remove(file);
        mIfRoomCounts.remove(file);
        mFirstAlwaysLocations.remove(file);
    }

    @Override
    @NonNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    @NonNull
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (FIELD_ALWAYS.equals(name)) {
                    mJavaAlwaysSeen = true;
                    if (mJavaAlwaysLocation == null) {
                        mJavaAlwaysLocation = context.getLocation(node);
                    }
                } else if (FIELD_IF_ROOM.equals(name)) {
                    mJavaIfRoomSeen = true;
                }
            }
        };
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mJavaAlwaysSeen = false;
        mJavaIfRoomSeen = false;
        mJavaAlwaysLocation = null;
        mAlwaysCounts.clear();
        mIfRoomCounts.clear();
        mFirstAlwaysLocations.clear();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mJavaAlwaysSeen && !mJavaIfRoomSeen && mJavaAlwaysLocation != null) {
            String message = "Avoid using `MenuItem.SHOW_AS_ACTION_ALWAYS`; use `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead";
            context.report(ISSUE, mJavaAlwaysLocation, message);
        }
    }
}