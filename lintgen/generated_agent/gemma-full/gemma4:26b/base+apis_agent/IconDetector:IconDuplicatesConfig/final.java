package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IdenticalIconAcrossConfigurations",
            "Identical icon across configurations",
            "The same resource content is provided in multiple configuration folders (e.g., drawable-hdpi and drawable-xhdpi). This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.NONE)
    );

    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList(".png", ".webp", ".jpg", ".jpeg"));
    private static final Map<String, String> fileHashCache = new ConcurrentHashMap<>();
    private static final Map<String, List<File>> resourceFilesCache = new ConcurrentHashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("android:src", "android:background", "android:drawableTop", "android:drawableLeft", "android:drawableRight", "android:drawableBottom");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String resourceName = extractResourceName(value);
        if (resourceName == null) {
            return;
        }

        try {
            String uri = attribute.getOwnerDocument().getBaseURI();
            if (uri == null) {
                return;
            }
            File currentXmlFile = new File(uri.replace("file://", ""));
            File resDir = findResDir(currentXmlFile);
            if (resDir == null) {
                return;
            }

            List<File> matches = resourceFilesCache.get(resourceName);
            if (matches == null) {
                matches = new ArrayList<>();
                searchRecursive(resDir, resourceName, matches);
                if (!matches.isEmpty()) {
                    resourceFilesCache.put(resourceName, matches);
                }
            }

            if (matches != null && matches.size() > 1) {
                Map<String, List<File>> hashGroups = new HashMap<>();
                for (File f : matches) {
                    String hash = getFileHash(f);
                    if (hash != null) {
                        hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(f);
                    }
                }

                for (Map.Entry<String, List<File>> entry : hashGroups.entrySet()) {
                    List<File> duplicates = entry.getValue();
                    if (duplicates.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < duplicates.size(); i++) {
                            sb.append(getRelativePathFromRes(resDir, duplicates.get(i)));
                            if (i < duplicates.size() - 1) {
                                sb.append(", ");
                            }
                        }
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                "Identical resource content found in: " + sb.toString(),
                                null
                        );
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors to prevent breaking the lint run
        }
    }

    private String extractResourceName(String value) {
        int slashIndex = value.indexOf('/');
        if (slashIndex == -1) {
            return value.substring(1).split("\\.")[0];
        }
        return value.substring(slashIndex + 1).split("\\.")[0];
    }

    private File findResDir(File currentXmlFile) {
        File dir = currentXmlFile.getParentFile();
        while (dir != null) {
            if (dir.getName().equals("res")) {
                return dir;
            }
            File resSibling = new File(dir, "res");
            if (resSibling.exists() && resSibling.isDirectory()) {
                return resSibling;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    private void searchRecursive(File dir, String name, List<File> matches) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                searchRecursive(f, name, matches);
            } else {
                String fn = f.getName().toLowerCase();
                if (fn.startsWith(name.toLowerCase() + ".") || fn.equals(name.toLowerCase())) {
                    int dotIndex = fn.lastIndexOf('.');
                    String ext = (dotIndex == -1) ? "" : fn.substring(dotIndex);
                    if (EXTENSIONS.contains(ext) || ext.isEmpty()) {
                        matches.add(f);
                    }
                }
            }
        }
    }

    private String getRelativePathFromRes(File resDir, File file) {
        String absPath = file.getAbsolutePath();
        String resAbsPath = resDir.getAbsolutePath();
        if (absPath.startsWith(resAbsPath)) {
            return absPath.substring(resAbsPath.length() + 1);
        }
        return file.getName();
    }

    private String getFileHash(File file) {
        String path = file.getAbsolutePath();
        if (fileHashCache.containsKey(path)) {
            return fileHashCache.get(path);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String hash = sb.toString();
            fileHashCache.put(path, hash);
            return hash;
        } catch (Exception e) {
            return null;
        }
    }
}