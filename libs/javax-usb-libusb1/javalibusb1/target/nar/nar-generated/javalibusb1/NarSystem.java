// DO NOT EDIT: Generated by NarSystemGenerate.
package javalibusb1;

/**
 * Generated class to load the correct version of the jni library
 *
 * @author maven-nar-plugin
 */
public final class NarSystem
{

    private NarSystem() 
    {
    }

   /**
    * Load jni library: javalibusb1-1.0.1-1-SNAPSHOT
    *
    * @author maven-nar-plugin
    */
    public static void loadLibrary()
    {
        System.loadLibrary("javalibusb1-1.0.1-1-SNAPSHOT");
    }

    public static int runUnitTests() {
	       return new NarSystem().runUnitTestsNative();
}

    public native int runUnitTestsNative();
}
