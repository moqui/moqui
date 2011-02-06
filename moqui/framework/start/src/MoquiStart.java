/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** This start class implements a ClassLoader and supports loading jars within a jar or war file in order to facilitate
 * an executable war file. To do this it overrides the findResource, findResources, and loadClass methods of the
 * ClassLoader class.
 *
 * The best source for research on the topic seems to be at http://www.jdotsoft.com, with a lot of good comments in the
 * JarClassLoader source file there.
 */
public class MoquiStart extends ClassLoader {

    public static void main(String[] args) throws IOException {
        String firstArg = args.length > 0 ? args[0] : "";

        // setup the class loader
        MoquiStart moquiStartLoader = new MoquiStart();
        Thread.currentThread().setContextClassLoader(moquiStartLoader);

        if ("-help".equals(firstArg) || "-?".equals(firstArg)) {
            System.out.println("");
            System.out.println("Usage: java -jar moqui.war [command] [arguments]");
            System.out.println("-help, -? ---- Help (this text)");
            System.out.println("-load -------- Run data loader");
            System.out.println("    -types=<type>[,<type>] -- Data types to load (can be anything, common are: seed, seed-initial, demo, ...)");
            System.out.println("    -location=<location> ---- Location of data file to load");
            System.out.println("    -timeout=<seconds> ------ Transaction timeout for each file");
            System.out.println("    -dummy-fks -------------- Use dummy foreign-keys to avoid referential integrity errors");
            System.out.println("    -use-try-insert --------- Try insert and update on error instead of checking for record first");
            System.out.println("  If no -types or -location argument is used all known data files of all types will be loaded.");
            System.out.println("[default] ---- Run simple server");
            System.out.println("");
            System.out.println("------------ Internal Class Path ------------");
            for (JarFile jf: moquiStartLoader.jarFileList)
                System.out.println("Jar file loaded: " + jf.getName());
            return;
        }


        // make a list of arguments, remove the first one (the command)
        List<String> argList = new ArrayList<String>(Arrays.asList(args));
        if (argList.size() > 0) argList.remove(0);
        Map<String, String> argMap = new HashMap<String, String>();
        for (String arg: argList) {
            if (arg.startsWith("-")) arg = arg.substring(1);
            if (arg.contains("=")) {
                argMap.put(arg.substring(0, arg.indexOf("=")), arg.substring(arg.indexOf("=")+1));
            } else {
                argMap.put(arg, "");
            }
        }

        // now run the command
        if ("-load".equals(firstArg)) {
            try {
                Class c = moquiStartLoader.loadClass("org.moqui.Moqui");
                Method m = c.getMethod("loadData", new Class<?>[] { Map.class });
                m.invoke(null, argMap);
            } catch (Exception e) {
                System.out.println("Error loading or running Moqui.loadData with args [" + argMap + "]: " + e.toString());
                e.printStackTrace();
                System.exit(0);
            }
        } else {
            // TODO
            System.out.println("The simple server is not yet implemented");
        }
    }

    protected JarFile outerFile = null;
    protected List<JarFile> jarFileList = new ArrayList<JarFile>();
    protected Map<String, Class<?>> classCache = new HashMap<String, Class<?>>();
    protected ProtectionDomain pd;

    public MoquiStart() {
        this(ClassLoader.getSystemClassLoader());
    }

    public MoquiStart(ClassLoader parent) {
        super(parent);

        URL wrapperWarUrl = null;
        try {
            // get outer file (the war file)
            pd = getClass().getProtectionDomain();
            CodeSource cs = pd.getCodeSource();
            wrapperWarUrl = cs.getLocation();
            outerFile = new JarFile(new File(wrapperWarUrl.toURI()));

            // allow for classes in the outerFile as well
            jarFileList.add(outerFile);

            Enumeration<JarEntry> jarEntries = outerFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry je = jarEntries.nextElement();
                if (je.isDirectory()) continue;
                String jeName = je.getName().toLowerCase();
                // get jars - mostly in the WEB-INF/lib directory, but can be anywhere
                if (jeName.lastIndexOf(".jar") == jeName.length() - 4) {
                    File file = createTempFile(je);
                    jarFileList.add(new JarFile(file));
                }
            }
        } catch (Exception e) {
            System.out.println("Error loading jars in war file [" + wrapperWarUrl + "]: " + e.toString());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // close all jarFiles so they will "deleteOnExit"
                for (JarFile jarFile : jarFileList) {
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        System.out.println("Error closing jar [" + jarFile + "] in war file [" + outerFile + "]: " + e.toString());
                    }
                }
            }
        });
    }

    protected File createTempFile(JarEntry je) throws IOException {
        byte[] jeBytes = getJarEntryBytes(outerFile, je);

        String tempName = je.getName().replace('/', '_') + ".";
        File file = File.createTempFile(tempName, null);
        file.deleteOnExit();
        BufferedOutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            os.write(jeBytes);
        } finally {
            if (os != null) os.close();
        }
        return file;
    }

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
        for (JarFile jarFile : jarFileList) {
            JarEntry jarEntry = jarFile.getJarEntry(resourceName);
            // to better support war format, look for the resourceName in the WEB-INF/classes directory
            if (jarEntry == null) jarEntry = jarFile.getJarEntry("WEB-INF/classes/" + resourceName);
            if (jarEntry != null) {
                try {
                    return new URL("jar:file:" + jarFile.getName() + "!/" + jarEntry);
                } catch (MalformedURLException e) {
                    System.out.println("Error making URL for [" + resourceName + "] in jar [" + jarFile + "] in war file [" + outerFile + "]: " + e.toString());
                }
            }
        }
        // NOTE: should we return null instead of this?
        return super.findResource(resourceName);
    }

    /** @see java.lang.ClassLoader#findResources(java.lang.String) */
    @Override
    public Enumeration<URL> findResources(String resourceName) throws IOException {
        List<URL> urlList = new ArrayList<URL>();
        for (JarFile jarFile : jarFileList) {
            JarEntry jarEntry = jarFile.getJarEntry(resourceName);
            // to better support war format, look for the resourceName in the WEB-INF/classes directory
            if (jarEntry == null) jarEntry = jarFile.getJarEntry("WEB-INF/classes/" + resourceName);
            if (jarEntry != null) {
                try {
                    urlList.add(new URL("jar:file:" + jarFile.getName() + "!/" + jarEntry));
                } catch (MalformedURLException e) {
                    System.out.println("Error making URL for [" + resourceName + "] in jar [" + jarFile + "] in war file [" + outerFile + "]: " + e.toString());
                }
            }
        }
        // add all resources found in parent loader too
        Enumeration<URL> superResources = super.findResources(resourceName);
        while (superResources.hasMoreElements()) urlList.add(superResources.nextElement());
        return Collections.enumeration(urlList);
    }

    @Override
    protected synchronized Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
        Class<?> c = null;
        try {
            try {
                c = findJarClass(className);
                if (c != null) return c;
            } catch (Exception e) {
                System.out.println("Error loading class [" + className + "] from jars in war file [" + outerFile.getName() + "]: " + e.toString());
                e.printStackTrace();
            }
            try {
                ClassLoader cl = getParent();
                c = cl.loadClass(className);
                return c;
            } catch (ClassNotFoundException e) { /* let the next one handle this */ }
            throw new ClassNotFoundException("Class [" + className + "] not found");
        } finally {
            if (c != null  &&  resolve) {
                resolveClass(c);
            }
        }
    }

    protected Class<?> findJarClass(String className) throws IOException, ClassFormatError {
        Class<?> c = classCache.get(className);
        if (c != null) return c;

        String classFileName = className.replace('.', '/') + ".class";
        for (JarFile jarFile: jarFileList) {
            JarEntry jarEntry = jarFile.getJarEntry(classFileName);
            // to better support war format, look for the resourceName in the WEB-INF/classes directory
            if (jarEntry == null) jarEntry = jarFile.getJarEntry("WEB-INF/classes/" + classFileName);
            if (jarEntry != null) {
                definePackage(className, jarFile);
                byte[] jeBytes = getJarEntryBytes(jarFile, jarEntry);
                if (jeBytes == null) {
                    System.out.println("Could not get bytes for [" + jarEntry.getName() + "] in [" + jarFile.getName() + "]");
                    continue;
                }
                // System.out.println("Class [" + classFileName + "] FOUND in jarFile [" + jarFile.getName() + "], size is " + (jeBytes == null ? "null" : jeBytes.length));
                c = defineClass(className, jeBytes, 0, jeBytes.length, pd);
                break;
            }
        }
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
