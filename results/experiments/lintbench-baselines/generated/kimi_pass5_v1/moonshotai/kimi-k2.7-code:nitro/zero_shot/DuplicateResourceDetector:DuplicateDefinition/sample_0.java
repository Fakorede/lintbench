package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class DuplicateResourceDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same resource "
                    + "more than once in the same resource folder is likely an error, for example "
                    + "attempting to add a new resource without realizing that the name is already used, "
                    + "and so on.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    private static final String VALUES_PREFIX = "values";
    private static final String EXT_XML = ".xml";

    private static final Set<String> NON_RESOURCE_TAGS = new HashSet<>(Arrays.asList(
            "skip", "eat-comment", "public", "public-group", "private"));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NotNull ResourceContext context, @NotNull File folder) {
        if (folder.getName().startsWith(VALUES_PREFIX)) {
            checkValuesFolder(context, folder);
        } else {
            checkFileBasedFolder(context, folder);
        }
    }

    private void checkFileBasedFolder(@NotNull ResourceContext context, @NotNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> seen = new HashMap<>();
        for (File file : files) {
            if (!file.isFile() || file.isHidden()) {
                continue;
            }

            String name = getResourceName(file);
            if (name == null) {
                continue;
            }

            File previous = seen.get(name);
            if (previous != null) {
                Location location = Location.create(file);
                location.setSecondary(Location.create(previous));

                String message = String.format(
                        "Duplicate resource `%1$s` defined in `%2$s`", name, folder.getName());
                context.report(ISSUE, location, message);
            } else {
                seen.put(name, file);
            }
        }
    }

    private void checkValuesFolder(@NotNull ResourceContext context, @NotNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, Location> seen = new HashMap<>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(EXT_XML)) {
                checkValuesXmlFile(context, file, seen);
            }
        }
    }

    private void checkValuesXmlFile(@NotNull ResourceContext context, @NotNull File file,
            @NotNull Map<String, Location> seen) {
        XMLStreamReader reader = null;
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            try {
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            } catch (Exception ignore) {
            }
            try {
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            } catch (Exception ignore) {
            }

            reader = factory.createXMLStreamReader(is);
            int depth = -1;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    if (depth != 1) {
                        continue;
                    }

                    String tag = reader.getLocalName();
                    if (NON_RESOURCE_TAGS.contains(tag)) {
                        continue;
                    }

                    String name = reader.getAttributeValue(null, "name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }

                    String type = tag;
                    if ("item".equals(tag)) {
                        String itemType = reader.getAttributeValue(null, "type");
                        if (itemType == null || itemType.isEmpty()) {
                            continue;
                        }
                        type = itemType;
                    }

                    String key = type + "/" + name;
                    int line = reader.getLocation().getLineNumber();
                    Location location = Location.create(file,
                            new DefaultPosition(line, -1, 0),
                            new DefaultPosition(line, -1, 0));

                    Location previous = seen.get(key);
                    if (previous != null) {
                        String message = String.format(
                                "Duplicate definition of resource `%1$s` (type %2$s)", name, type);
                        location.setSecondary(previous);
                        context.report(ISSUE, location, message);
                    } else {
                        seen.put(key, location);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
        } catch (Exception e) {
            // Ignore parse errors here; other checks report malformed XML.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                }
            }
        }
    }

    private String getResourceName(@NotNull File file) {
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        return fileName.substring(0, dot);
    }
}