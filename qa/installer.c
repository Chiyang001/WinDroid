#define _UNICODE
#define COBJMACROS
#include <windows.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <wchar.h>

static BOOL install_program(HINSTANCE instance, const wchar_t *path) {
    HRSRC resource = FindResourceW(instance, MAKEINTRESOURCEW(101), RT_RCDATA);
    if (!resource) return FALSE;
    HGLOBAL loaded = LoadResource(instance, resource);
    DWORD size = SizeofResource(instance, resource);
    const void *bytes = loaded ? LockResource(loaded) : NULL;
    if (!bytes || !size) return FALSE;
    HANDLE file = CreateFileW(path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, 0, NULL);
    if (file == INVALID_HANDLE_VALUE) return FALSE;
    DWORD written;
    BOOL complete = WriteFile(file, bytes, size, &written, NULL) && written == size;
    CloseHandle(file);
    return complete;
}

static HRESULT add_shortcut(const wchar_t *target, const wchar_t *working_directory) {
    wchar_t desktop[MAX_PATH];
    HRESULT result = SHGetFolderPathW(NULL, CSIDL_DESKTOPDIRECTORY | CSIDL_FLAG_CREATE, NULL, SHGFP_TYPE_CURRENT, desktop);
    if (FAILED(result) || wcslen(desktop) >= MAX_PATH - 22) return E_FAIL;
    wcscat(desktop, L"\\WinDroid QA x64.lnk");

    result = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if (FAILED(result)) return result;
    IShellLinkW *link = NULL;
    IPersistFile *persist = NULL;
    result = CoCreateInstance(&CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, &IID_IShellLinkW, (void **)&link);
    if (SUCCEEDED(result)) result = IShellLinkW_SetPath(link, target);
    if (SUCCEEDED(result)) result = IShellLinkW_SetWorkingDirectory(link, working_directory);
    if (SUCCEEDED(result)) result = IShellLinkW_QueryInterface(link, &IID_IPersistFile, (void **)&persist);
    if (SUCCEEDED(result)) result = IPersistFile_Save(persist, desktop, TRUE);
    if (persist) IPersistFile_Release(persist);
    if (link) IShellLinkW_Release(link);
    CoUninitialize();
    return result;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    if (MessageBoxW(NULL, L"Install the x64 test program into C:\\WinDroidQA and add a Windows desktop shortcut?",
            L"WinDroid QA installer (x86)", MB_OKCANCEL | MB_ICONQUESTION) != IDOK) return 0;

    const wchar_t *directory = L"C:\\WinDroidQA";
    const wchar_t *target = L"C:\\WinDroidQA\\WinDroid QA x64.exe";
    if ((!CreateDirectoryW(directory, NULL) && GetLastError() != ERROR_ALREADY_EXISTS)
            || !install_program(instance, target)) {
        MessageBoxW(NULL, L"Could not install the test program.", L"WinDroid QA installer", MB_OK | MB_ICONERROR);
        return 1;
    }
    BOOL has_link = SUCCEEDED(add_shortcut(target, directory));
    MessageBoxW(NULL, has_link ? L"Installation completed with a desktop shortcut." : L"Installed. Browse C:\\WinDroidQA to open the program.",
            L"WinDroid QA installer", MB_OK | MB_ICONINFORMATION);
    return 0;
}
