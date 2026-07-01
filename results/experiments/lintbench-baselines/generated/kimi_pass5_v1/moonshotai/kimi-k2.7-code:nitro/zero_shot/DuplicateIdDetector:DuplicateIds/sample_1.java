package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.NEW_ID_PREFIX;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate IDs within a single layout",
            "Using the same id for more than one view in the same layout can cause "
                    + "`findViewById()` to return an unexpected view.",
            Category.LAYOUT,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, Location> mIds;

    @Override
    public boolean appliesTo(@NonNull EnumSet<Scope> scope, @NonNull XmlContext context) {
        return context.getResourceFolderType() == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        mIds = null;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        String id = getIdName(value);
        if (id == null) {
            return;
        }

        Location location = context.getLocation(attribute);
        Location previous = mIds.get(id);
        if (previous != null) {
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    String.format(
                            "Duplicate id `%1$s` in this layout; the same id was first used at line %2$d",
                            value,
                            previous.getStart().getLine()));
        } else {
            mIds.put(id, location);
        }
    }

    private static String getIdName(@NonNull String value) {
        if (value.startsWith(NEW_ID_PREFIX)) {
            return value.substring(NEW_ID_PREFIX.length());
        }
        if (value.startsWith("@id/")) {
            return value.substring("@id/".length());
        }
        return null;
    }
}