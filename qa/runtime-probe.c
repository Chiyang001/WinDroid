#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

int wmain(int argc, wchar_t **argv) {
    if (argc >= 3 && !wcscmp(argv[1], L"wait")) {
        STARTUPINFOW startup = {0};
        PROCESS_INFORMATION child;
        startup.cb = sizeof(startup);
        if (!CreateProcessW(NULL, argv[2], NULL, NULL, TRUE, 0, NULL, NULL, &startup, &child)) {
            printf("CreateProcess error=%lu\n", GetLastError());
            return 1;
        }
        CloseHandle(child.hThread);
        DWORD status = 0;
        BOOL exited = WaitForSingleObject(child.hProcess, 45000) == WAIT_OBJECT_0
                && GetExitCodeProcess(child.hProcess, &status);
        printf("Windows child exit=%s%lu (0x%08lx)\n", exited ? "" : "unavailable ", status, status);
        CloseHandle(child.hProcess);
        return exited && status == 0 ? 0 : 1;
    }
    if (argc != 3) return 2;

    HMODULE library = LoadLibraryW(argv[1]);
    if (!library) {
        printf("LoadLibrary error=%lu\n", GetLastError());
        return 1;
    }
    char symbol[128];
    if (!WideCharToMultiByte(CP_UTF8, 0, argv[2], -1, symbol, sizeof(symbol), NULL, NULL)) return 2;
    wchar_t path[MAX_PATH];
    if (!GetModuleFileNameW(library, path, MAX_PATH)) return 1;
    DWORD handle;
    DWORD size = GetFileVersionInfoSizeW(path, &handle);
    void *version = size ? malloc(size) : NULL;
    VS_FIXEDFILEINFO *info;
    UINT length;
    if (version && GetFileVersionInfoW(path, 0, size, version)
            && VerQueryValueW(version, L"\\", (void **)&info, &length) && length >= sizeof(*info)) {
        printf("File version=%u.%u.%u.%u\n", HIWORD(info->dwFileVersionMS), LOWORD(info->dwFileVersionMS),
                HIWORD(info->dwFileVersionLS), LOWORD(info->dwFileVersionLS));
    }
    free(version);
    BOOL available = GetProcAddress(library, symbol) != NULL;
    printf("DLL=%ls export %s=%s\n", path, symbol, available ? "available" : "missing");
    FreeLibrary(library);
    return available ? 0 : 1;
}
