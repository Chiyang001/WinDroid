#define _UNICODE
#include <windows.h>
#include <wchar.h>

#ifdef _WIN64
#define ARCH L"x64"
#else
#define ARCH L"x86"
#endif

static HWND input;
static HWND status;
static wchar_t data_path[MAX_PATH];

static LRESULT CALLBACK handle_window(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    switch (message) {
        case WM_CREATE: {
            HFONT font = GetStockObject(DEFAULT_GUI_FONT);
            HWND prompt = CreateWindowW(L"STATIC", L"WinDroid Windows test program (" ARCH L")",
                    WS_CHILD | WS_VISIBLE, 16, 16, 350, 22, window, NULL, NULL, NULL);
            input = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_AUTOHSCROLL,
                    16, 48, 337, 30, window, NULL, NULL, NULL);
            HWND save = CreateWindowW(L"BUTTON", L"Save test text", WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_PUSHBUTTON,
                    16, 91, 140, 34, window, (HMENU)1, NULL, NULL);
            status = CreateWindowW(L"STATIC", L"Type in the box and save to test persistence.", WS_CHILD | WS_VISIBLE,
                    16, 137, 345, 22, window, NULL, NULL, NULL);
            SendMessageW(prompt, WM_SETFONT, (WPARAM)font, TRUE);
            SendMessageW(input, WM_SETFONT, (WPARAM)font, TRUE);
            SendMessageW(save, WM_SETFONT, (WPARAM)font, TRUE);
            SendMessageW(status, WM_SETFONT, (WPARAM)font, TRUE);
            SendMessageW(input, EM_SETLIMITTEXT, 250, 0);

            HANDLE file = CreateFileW(data_path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
            if (file != INVALID_HANDLE_VALUE) {
                wchar_t text[256] = {0};
                DWORD count;
                if (ReadFile(file, text, sizeof(text) - sizeof(wchar_t), &count, NULL)) {
                    SetWindowTextW(input, text);
                    SetWindowTextW(status, L"Restored previously saved text.");
                }
                CloseHandle(file);
            }
            SetFocus(input);
            return 0;
        }
        case WM_COMMAND:
            if (LOWORD(wparam) == 1) {
                wchar_t text[256];
                DWORD bytes = GetWindowTextW(input, text, 256) * sizeof(wchar_t);
                HANDLE file = CreateFileW(data_path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, 0, NULL);
                DWORD written;
                BOOL saved = file != INVALID_HANDLE_VALUE && WriteFile(file, text, bytes, &written, NULL) && written == bytes;
                if (file != INVALID_HANDLE_VALUE) CloseHandle(file);
                SetWindowTextW(status, saved ? L"Saved. Close and reopen the program." : L"Could not save test text.");
                return 0;
            }
            break;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(window, message, wparam, lparam);
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    DWORD size = GetEnvironmentVariableW(L"APPDATA", data_path, MAX_PATH);
    if (size == 0 || size >= MAX_PATH - 25) return 1;
    wcscat(data_path, L"\\WinDroidQA-" ARCH L".txt");

    WNDCLASSW window_class = {0};
    window_class.lpfnWndProc = handle_window;
    window_class.hInstance = instance;
    window_class.hCursor = LoadCursorW(NULL, IDC_ARROW);
    window_class.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    window_class.lpszClassName = L"WinDroidQA";
    if (!RegisterClassW(&window_class)) return 1;

    HWND window = CreateWindowW(window_class.lpszClassName, L"WinDroid QA " ARCH,
            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU, 120, 100, 390, 218, NULL, NULL, instance, NULL);
    if (!window) return 1;
    ShowWindow(window, show);
    UpdateWindow(window);

    MSG message;
    while (GetMessageW(&message, NULL, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return 0;
}
