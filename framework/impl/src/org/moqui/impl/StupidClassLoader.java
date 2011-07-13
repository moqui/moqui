package org.moqui.impl;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class StupidClassLoader extends ClassLoader {
    public static final Map<String, Class<?>> commonJavaClassesMap = createCommonJavaClassesMap();
    protected static Map<String, Class<?>> createCommonJavaClassesMap() {
        Map<String, Class<?>> m = new HashMap<String, Class<?>>();
        m.put("java.lang.String",java.lang.String.class); m.put("String", java.lang.String.class);
        m.put("java.sql.Timestamp", java.sql.Timestamp.class); m.put("Timestamp", java.sql.Timestamp.class);
        m.put("java.sql.Time", java.sql.Time.class); m.put("Time", java.sql.Time.class);
        m.put("java.sql.Date", java.sql.Date.class); m.put("Date", java.sql.Date.class);
        m.put("java.util.Locale", java.util.Locale.class); m.put("java.util.TimeZone", java.util.TimeZone.class);
        m.put("java.lang.Byte", java.lang.Byte.class); m.put("java.lang.Character", java.lang.Character.class);
        m.put("java.lang.Integer", java.lang.Integer.class); m.put("Integer", java.lang.Integer.class);
        m.put("java.lang.Long", java.lang.Long.class); m.put("Long", java.lang.Long.class);
        m.put("java.lang.Short", java.lang.Short.class);
        m.put("java.lang.Float", java.lang.Float.class); m.put("Float", java.lang.Float.class);
        m.put("java.lang.Double", java.lang.Double.class); m.put("Double", java.lang.Double.class);
        m.put("java.math.BigDecimal", java.math.BigDecimal.class); m.put("BigDecimal", java.math.BigDecimal.class);
        m.put("java.math.BigInteger", java.math.BigInteger.class); m.put("BigInteger", java.math.BigInteger.class);
        m.put("java.lang.Boolean", java.lang.Boolean.class); m.put("Boolean", java.lang.Boolean.class);
        m.put("java.lang.Object", java.lang.Object.class); m.put("Object", java.lang.Object.class);
        m.put("java.sql.Blob", java.sql.Blob.class); m.put("Blob", java.sql.Blob.class);
        m.put("java.nio.ByteBuffer", java.nio.ByteBuffer.class);
        m.put("java.sql.Clob", java.sql.Clob.class); m.put("Clob", java.sql.Clob.class);
        m.put("java.util.Date", java.util.Date.class);
        m.put("java.util.Collection", java.util.Collection.class); m.put("Collection", java.util.Collection.class);
        m.put("java.util.List", java.util.List.class); m.put("List", java.util.List.class);
        m.put("java.util.Map", java.util.Map.class); m.put("Map", java.util.Map.class); m.put("java.util.HashMap", java.util.HashMap.class);
        m.put("java.util.Set", java.util.Set.class); m.put("Set", java.util.Set.class); m.put("java.util.HashSet", java.util.HashSet.class);
        m.put(Boolean.TYPE.getName(), Boolean.TYPE); m.put(Short.TYPE.getName(), Short.TYPE);
        m.put(Integer.TYPE.getName(), Integer.TYPE); m.put(Long.TYPE.getName(), Long.TYPE);
        m.put(Float.TYPE.getName(), Float.TYPE); m.put(Double.TYPE.getName(), Double.TYPE);
        m.put(Byte.TYPE.getName(), Byte.TYPE); m.put(Character.TYPE.getName(), Character.TYPE);
        return m;
    }

    protected final List<JarFile> jarFileList = new ArrayList<JarFile>();
    protected final Map<String, Class> classCache = new HashMap<String, Class>();
    protected final Map<String, URL> resourceCache = new HashMap<String, URL>();
    protected ProtectionDomain pd;

    public StupidClassLoader(ClassLoader parent) {
        super(parent);

        if (parent == null) throw new IllegalArgumentException("Parent ClassLoader cannot be null");

        pd = getClass().getProtectionDomain();

        for (Map.Entry commonClassEntry: commonJavaClassesMap.entrySet())
            classCache.put((String) commonClassEntry.getKey(), (Class) commonClassEntry.getValue());
    }

    public void addJarFile(JarFile jf) { jarFileList.add(jf); }
    //List<JarFile> getJarFileList() { return jarFileList; }
    //Map<String, Class> getClassCache() { return classCache; }
    //Map<String, URL> getResourceCache() { return resourceCache; }

    protected byte[] getJarEntryBytes(JarFile jarFile, JarEntry je) throws IOException {
        DataInputStream dis = null;
        byte[] jeBytes = null;
        try {
            long lSize = je.getSize();
            if (lSize <= 0  ||  lSize >= Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Size [" + lSize + "] not valid for war entry [" + je + "]");
            }
            jeBytes = new byte[(int)lSize];
            InputStream is = jarFile.getInputStream(je);
            dis = new DataInputStream(is);
            dis.readFully(jeBytes);
        } finally {
            if (dis != null) dis.close();
        }
        return jeBytes;
    }

    /** @see java.lang.ClassLoader#findResource(java.lang.String) */
    @Override
    protected URL findResource(String resourceName) {
        if (resourceCache.containsKey(resourceName)) return resourceCache.get(resourceName);

        URL resourceUrl = null;
        for (JarFile jarFile : jarFileList) {
            JarEntry jarEntry = jarFile.getJarEntry(resourceName);
            if (jarEntry != null) {
                try {
                    String jarFileName = jarFile.getName();
                    if (jarFileName.contains("\\")) jarFileName = jarFileName.replace('\\', '/');
                    resourceUrl = new URL("jar:file:" + jarFileName + "!/" + jarEntry);
                } catch (MalformedURLException e) {
                    System.out.println("Error making URL for [" + resourceName + "] in jar [" + jarFile + "]: " + e.toString());
                }
            }
        }

        if (resourceUrl == null) resourceUrl = super.findResource(resourceName);
        resourceCache.put(resourceName, resourceUrl);
        return resourceUrl;
    }

    /** @see java.lang.ClassLoader#findResources(java.lang.String) */
    @Override
    public Enumeration<URL> findResources(String resourceName) throws IOException {
        List<URL> urlList = new ArrayList<URL>();
        for (JarFile jarFile : jarFileList) {
            JarEntry jarEntry = jarFile.getJarEntry(resourceName);
            if (jarEntry != null) {
                try {
                    String jarFileName = jarFile.getName();
                    if (jarFileName.contains("\\")) jarFileName = jarFileName.replace('\\', '/');
                    urlList.add(new URL("jar:file:" + jarFileName + "!/" + jarEntry));
                } catch (MalformedURLException e) {
                    System.out.println("Error making URL for [" + resourceName + "] in jar [" + jarFile + "]: " + e.toString());
                }
            }
        }
        // add all resources found in parent loader too
        Enumeration<URL> superResources = super.findResources(resourceName);
        while (superResources.hasMoreElements()) urlList.add(superResources.nextElement());
        return Collections.enumeration(urlList);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException { return loadClass(name, false); }

    @Override
    protected synchronized Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
        if (classCache.containsKey(className)) {
            Class<?> cl = classCache.get(className);
            if (cl == null) throw new ClassNotFoundException("Class " + className + " not found.");
            return cl;
        }

        Class<?> c = null;
        try {
            try {
                c = findJarClass(className);
            } catch (Exception e) {
                System.out.println("Error loading class [" + className + "] from additional jars: " + e.toString());
                e.printStackTrace();
            }

            if (c == null) {
                try {
                    ClassLoader cl = getParent();
                    c = cl.loadClass(className);
                } catch (ClassNotFoundException e) {
                    // remember that the class is not found
                    classCache.put(className, null);
                    // e.printStackTrace();
                    throw e;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                }
            }

            // System.out.println("Loading class name [" + className + "] got class: " + c);
            classCache.put(className, c);
            return c;
        } finally {
            if (c != null  &&  resolve) {
                resolveClass(c);
            }
        }
    }

    protected Class<?> findJarClass(String className) throws IOException, ClassFormatError {
        if (classCache.containsKey(className)) return classCache.get(className);

        Class<?> c = null;
        String classFileName = className.replace('.', '/') + ".class";

        for (JarFile jarFile: jarFileList) {
            // System.out.println("Finding class file " + classFileName + " in jar file " + jarFile.getName());
            JarEntry jarEntry = jarFile.getJarEntry(classFileName);
            if (jarEntry != null) {
                definePackage(className, jarFile);
                byte[] jeBytes = getJarEntryBytes(jarFile, jarEntry);
                if (jeBytes == null) {
                    System.out.println("Could not get bytes for [" + jarEntry.getName() + "] in [" + jarFile.getName() + "]");
                    continue;
                }
                // System.out.println("Class [" + classFileName + "] FOUND in jarFile [" + jarFile.getName() + "], size is " + jeBytes.length);
                c = defineClass(className, jeBytes, 0, jeBytes.length, pd);
                break;
            }
        }
        // down here only cache if found
        if (c != null) classCache.put(className, c);
        return c;
    }

    protected void definePackage(String className, JarFile jarFile) throws IllegalArgumentException {
        Manifest mf;
        try {
            mf = jarFile.getManifest();
        } catch (IOException e) {
            // use default manifest
            mf = new Manifest();
        }
        int dotIndex = className.lastIndexOf('.');
        String packageName = dotIndex > 0 ? className.substring(0, dotIndex) : "";
        if (getPackage(packageName) == null) {
            definePackage(packageName,
                    mf.getMainAttributes().getValue(Attributes.Name.SPECIFICATION_TITLE),
                    mf.getMainAttributes().getValue(Attributes.Name.SPECIFICATION_VERSION),
                    mf.getMainAttributes().getValue(Attributes.Name.SPECIFICATION_VENDOR),
                    mf.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_TITLE),
                    mf.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION),
                    mf.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VENDOR),
                    getSealURL(mf));
        }
    }

    protected URL getSealURL(Manifest mf) {
        String seal = mf.getMainAttributes().getValue(Attributes.Name.SEALED);
        if (seal == null) return null;
        try {
            return new URL(seal);
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
