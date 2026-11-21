using System;
using System.Runtime.InteropServices;

internal static class NativeFrameworkProbe
{
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr GetModuleHandle(string moduleName);

    private static int Main()
    {
        bool nativeClr = GetModuleHandle("clr.dll") != IntPtr.Zero;
        Console.WriteLine("{0} CLR {1}, native clr.dll={2}, runtime={3}",
            Environment.Is64BitProcess ? "x64" : "x86", Environment.Version,
            nativeClr, RuntimeEnvironment.GetRuntimeDirectory());
        return nativeClr ? 0 : 1;
    }
}
