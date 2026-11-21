#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wchar.h>

static DWORD run_process(PCWSTR program, PWSTR arguments, PCWSTR directory, BOOL wait_for_children) {
    STARTUPINFOW startup = {0};
    PROCESS_INFORMATION child;
    DWORD status;
    HANDLE job = wait_for_children ? CreateJobObjectW(NULL, NULL) : NULL;
    if (wait_for_children && !job) return 0x10000 | GetLastError();
    startup.cb = sizeof(startup);
    if (!CreateProcessW(program, arguments, NULL, NULL, FALSE, job ? CREATE_SUSPENDED : 0,
            NULL, directory, &startup, &child)) {
        status = 0x10000 | GetLastError();
    } else if (job && (!AssignProcessToJobObject(job, child.hProcess) || ResumeThread(child.hThread) == (DWORD)-1)) {
        status = 0x10000 | GetLastError();
        TerminateProcess(child.hProcess, status);
        CloseHandle(child.hThread);
        CloseHandle(child.hProcess);
    }
    else {
        CloseHandle(child.hThread);
        if (WaitForSingleObject(child.hProcess, INFINITE) != WAIT_OBJECT_0
                || !GetExitCodeProcess(child.hProcess, &status)) status = 0x10000 | GetLastError();
        CloseHandle(child.hProcess);
        if (job) {
            JOBOBJECT_BASIC_ACCOUNTING_INFORMATION accounting;
            for (;;) {
                if (!QueryInformationJobObject(job, JobObjectBasicAccountingInformation, &accounting, sizeof(accounting), NULL)) {
                    status = 0x10000 | GetLastError();
                    break;
                }
                if (!accounting.ActiveProcesses) break;
                Sleep(100);
            }
        }
    }
    if (job) CloseHandle(job);
    return status;
}

static DWORD run_installer(PWSTR arguments) {
    return run_process(NULL, arguments, NULL, FALSE);
}

static DWORD uninstall_app(void) {
    wchar_t payload[8193];
    HANDLE file = CreateFileW(L"Z:\\tmp\\windroid-app-uninstall", GENERIC_READ, FILE_SHARE_READ,
            NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) return 0x10000 | GetLastError();
    DWORD bytes;
    BOOL read = ReadFile(file, payload, sizeof(payload), &bytes, NULL);
    CloseHandle(file);
    if (!read || bytes >= sizeof(payload) || bytes % sizeof(*payload) || bytes < 3 * sizeof(*payload)) {
        return 0x10000 | ERROR_INVALID_DATA;
    }
    wchar_t *end = payload + bytes / sizeof(*payload) - 1;
    if (*end) return 0x10000 | ERROR_INVALID_DATA;
    wchar_t *directory = payload + wcslen(payload) + 1;
    if (directory >= end || !*directory) return 0x10000 | ERROR_INVALID_DATA;
    wchar_t *arguments = directory + wcslen(directory) + 1;
    if (arguments >= end || !*arguments) return 0x10000 | ERROR_INVALID_DATA;
    return run_process(payload, arguments, directory, TRUE);
}

static DWORD run_vc14(BOOL x86, BOOL remove) {
    const wchar_t *system = x86 ? L"syswow64" : L"system32";
    const wchar_t *arch = x86 ? L"x86" : L"x64";
    const wchar_t *names[] = {L"Minimum", L"Additional"};
    const wchar_t *codes_x86[] = {L"{EF48C1C6-7072-44D8-83B5-426456C66056}", L"{AD5BEED8-A1B1-4D5F-96CD-813286F54B8E}"};
    const wchar_t *codes_x64[] = {L"{B3040AC3-4248-4745-83E9-DD2B95B3CF73}", L"{24486C0E-319F-4DF7-A234-5F6B153E4846}"};
    DWORD result = ERROR_SUCCESS;
    for (int part = 0; part < 2; ++part) {
        wchar_t command[512];
        if (remove) {
            const wchar_t *product = (x86 ? codes_x86 : codes_x64)[1 - part];
            swprintf(command, ARRAYSIZE(command), L"C:\\windows\\%ls\\msiexec.exe /x %ls /qn REBOOT=ReallySuppress NOVSUI=1", system, product);
        } else {
            swprintf(command, ARRAYSIZE(command), L"C:\\windows\\%ls\\msiexec.exe /i Z:\\opt\\windroid-runtime\\vc14-%ls\\%ls\\runtime.msi /qn REBOOT=ReallySuppress NOVSUI=1 MSIFASTINSTALL=7", system, arch, names[part]);
        }
        DWORD status = run_installer(command);
        if (status == ERROR_SUCCESS_REBOOT_REQUIRED) result = status;
        else if (status != ERROR_SUCCESS && !(remove && status == ERROR_UNKNOWN_PRODUCT)) return status;
    }
    return result;
}

static DWORD register_framework_shim(BOOL x86) {
    const wchar_t *system = x86 ? L"syswow64" : L"system32";
    const wchar_t *arch = x86 ? L"x86" : L"x64";
    const wchar_t *root = x86 ? L"C:\\windows\\Microsoft.NET\\Framework\\"
                              : L"C:\\windows\\Microsoft.NET\\Framework64\\";
    wchar_t source[MAX_PATH], destination[MAX_PATH];
    swprintf(destination, ARRAYSIZE(destination), L"C:\\windows\\%ls\\mui", system);
    if (!CreateDirectoryW(destination, NULL) && GetLastError() != ERROR_ALREADY_EXISTS) return GetLastError();
    swprintf(destination, ARRAYSIZE(destination), L"C:\\windows\\%ls\\mui\\0409", system);
    if (!CreateDirectoryW(destination, NULL) && GetLastError() != ERROR_ALREADY_EXISTS) return GetLastError();
    swprintf(source, ARRAYSIZE(source), L"Z:\\opt\\windroid-runtime\\framework48\\shim\\%ls\\mscorees.dll", arch);
    swprintf(destination, ARRAYSIZE(destination), L"C:\\windows\\%ls\\mui\\0409\\mscorees.dll", system);
    if (!CopyFileW(source, destination, FALSE)) return GetLastError();
    swprintf(source, ARRAYSIZE(source), L"Z:\\opt\\windroid-runtime\\framework48\\shim\\%ls\\mscoree.dll", arch);
    swprintf(destination, ARRAYSIZE(destination), L"C:\\windows\\%ls\\mscoree.dll", system);
    if (!CopyFileW(source, destination, FALSE)) return GetLastError();

    HKEY framework;
    LONG error = RegCreateKeyExW(HKEY_LOCAL_MACHINE, L"Software\\Microsoft\\.NETFramework",
            0, NULL, REG_OPTION_NON_VOLATILE, KEY_SET_VALUE | (x86 ? KEY_WOW64_32KEY : KEY_WOW64_64KEY),
            NULL, &framework, NULL);
    if (error != ERROR_SUCCESS) return error;
    error = RegSetValueExW(framework, L"InstallRoot", 0, REG_SZ, (const BYTE *)root,
            (DWORD)((wcslen(root) + 1) * sizeof(*root)));
    if (error == ERROR_SUCCESS) {
        DWORD latest = 1;
        error = RegSetValueExW(framework, L"OnlyUseLatestCLR", 0, REG_DWORD,
                (const BYTE *)&latest, sizeof(latest));
    }
    RegCloseKey(framework);
    return error;
}

static DWORD run_framework48(void) {
    wchar_t command[] = L"C:\\windows\\system32\\msiexec.exe /i "
            L"Z:\\opt\\windroid-runtime\\framework48\\netfx_Full_x64.msi "
            L"PATCH=Z:\\opt\\windroid-runtime\\framework48\\netfx_Patch_x64.msp /qn REBOOT=ReallySuppress NONGEN=1";
    DWORD status = run_installer(command);
    if (status != ERROR_SUCCESS && status != ERROR_SUCCESS_REBOOT_REQUIRED) return status;
    DWORD error = register_framework_shim(TRUE);
    if (error == ERROR_SUCCESS) error = register_framework_shim(FALSE);
    return error == ERROR_SUCCESS ? status : 0x10000 | error;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
    DWORD status;
    if (!wcscmp(arguments, L"--vc14-x86 install")) status = run_vc14(TRUE, FALSE);
    else if (!wcscmp(arguments, L"--vc14-x86 remove")) status = run_vc14(TRUE, TRUE);
    else if (!wcscmp(arguments, L"--vc14-x64 install")) status = run_vc14(FALSE, FALSE);
    else if (!wcscmp(arguments, L"--vc14-x64 remove")) status = run_vc14(FALSE, TRUE);
    else if (!wcscmp(arguments, L"--framework48 install")) status = run_framework48();
    else if (!wcscmp(arguments, L"--app-uninstall")) status = uninstall_app();
    else status = run_installer(arguments);

    HANDLE report = CreateFileW(L"Z:\\tmp\\windroid-runtime-exit", GENERIC_WRITE, 0, NULL,
            CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (report == INVALID_HANDLE_VALUE) return 1;
    DWORD written;
    BOOL saved = WriteFile(report, &status, sizeof(status), &written, NULL) && written == sizeof(status);
    CloseHandle(report);
    return saved ? 0 : 1;
}
