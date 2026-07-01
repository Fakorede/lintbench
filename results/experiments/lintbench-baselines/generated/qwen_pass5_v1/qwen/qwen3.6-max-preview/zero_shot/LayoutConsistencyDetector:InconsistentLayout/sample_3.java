package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "InconsistentLayout",
        "Inconsistent Layouts",
        "This check ensures that a layout resource which is defined in multiple resource folders, " +
        "specifies the same set of widgets.\n\n" +
        "This finds cases where you have accidentally forgotten to add a widget to all variations " +
        "of the layout, which could result in a runtime crash for some resource configurations when " +
        "a `findViewById()` fails.\n\n" +
        "There **are** cases where this is intentional. For example, you may have a dedicated large " +
        "tablet layout which adds some extra widgets that are not present in the phone version of the " +
        "layout. As long as the code accessing the layout resource is careful to handle this properly, " +
        "it is valid. In that case, you can suppress this lint check for the given extra or missing views, " +
        "or the whole layout",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(LayoutConsistencyDetector.class, Scope.ALL_RESOURCE_FILES_SCOPE)
    );

    private Map<String, List<LayoutVariation>> layoutMap = new HashMap<>();
    private LayoutVariation currentVariation;

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        String fileName = context.file.getName();
        if (!fileName.endsWith(SdkConstants.DOT_XML)) {
            return;
        }

        String layoutName = fileName.substring(0, fileName.length() - SdkConstants.DOT_XML.length());
        File resFolder = xmlContext.getResourceFolder();
        if (resFolder == null) {
            return;
        }
        String config = resFolder.getName();

        currentVariation = new LayoutVariation();
        currentVariation.file = context.file;
        currentVariation.config = config;
        currentVariation.rootLocation = Location.create(context.file);

        layoutMap.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(currentVariation);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (currentVariation == null) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = extractIdName(id);
            if (idName != null) {
                currentVariation.ids.add(idName);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutVariation>> entry : layoutMap.entrySet()) {
            List<LayoutVariation> variations = entry.getValue();
            if (variations.size() < 2) {
                continue;
            }

            Set<String> union = new HashSet<>();
            for (LayoutVariation v : variations) {
                union.addAll(v.ids);
            }

            for (LayoutVariation v : variations) {
                Set<String> missing = new HashSet<>(union);
                missing.removeAll(v.ids);
                if (!missing.isEmpty()) {
                    String msg = String.format(
                        "The layout `%s` in configuration `%s` is missing the following IDs found in other configurations: %s",
                        entry.getKey(), v.config, missing
                    );
                    context.report(ISSUE, v.rootLocation, msg);
                }
            }
        }
        layoutMap.clear();
    }

    @Nullable
    private static String extractIdName(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        }
        if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return null;
    }

    private static class LayoutVariation {
        File file;
        String config;
        Set<String> ids = new HashSet<>();
        Location rootLocation;
    }
}