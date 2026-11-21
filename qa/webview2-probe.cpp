#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <WebView2.h>
#include <cstdio>
#include <cwchar>

#ifdef __MINGW32__
__CRT_UUID_DECL(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler,
        0x4e8a3389, 0xc9d8, 0x4bd2, 0xb6, 0xb5, 0x12, 0x4f, 0xee, 0x6c, 0xc1, 0x4d)
__CRT_UUID_DECL(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler,
        0x6c4819f3, 0xc9b7, 0x4260, 0x81, 0x27, 0xc9, 0xf5, 0xbd, 0xe7, 0xf6, 0x8c)
#endif

static HRESULT callbackResult = E_PENDING;
static ICoreWebView2Controller *controller;
static ICoreWebView2 *view;
static HWND window;

template <typename Interface>
class Completion : public Interface {
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID iid, void **value) override {
        if (iid != __uuidof(IUnknown) && iid != __uuidof(Interface)) {
            *value = nullptr;
            return E_NOINTERFACE;
        }
        *value = static_cast<Interface *>(this);
        AddRef();
        return S_OK;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return InterlockedIncrement(&references); }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG remaining = InterlockedDecrement(&references);
        if (!remaining) delete this;
        return remaining;
    }
    virtual ~Completion() = default;

private:
    LONG references = 1;
};

class ControllerReady : public Completion<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler> {
public:
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2Controller *created) override {
        callbackResult = result;
        if (FAILED(result) || !created) return S_OK;
        controller = created;
        controller->AddRef();
        RECT bounds{0, 0, 640, 480};
        controller->put_Bounds(bounds);
        callbackResult = controller->get_CoreWebView2(&view);
        if (SUCCEEDED(callbackResult)) {
            callbackResult = view->NavigateToString(L"<html><body>WinDroid WebView2 QA"
                L"<script>document.title='WinDroid WebView2 '+(6*7)</script></body></html>");
        }
        return S_OK;
    }
};

class EnvironmentReady : public Completion<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler> {
public:
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2Environment *environment) override {
        if (FAILED(result) || !environment) {
            callbackResult = result;
            return S_OK;
        }
        PWSTR version = nullptr;
        if (SUCCEEDED(environment->get_BrowserVersionString(&version))) {
            printf("Environment runtime=%ls\n", version);
            fflush(stdout);
            CoTaskMemFree(version);
        }
        auto ready = new ControllerReady();
        result = environment->CreateCoreWebView2Controller(window, ready);
        ready->Release();
        if (FAILED(result)) callbackResult = result;
        return S_OK;
    }
};

int wmain(int argc, wchar_t **argv) {
    if (argc != 1 && argc != 3) return 2;
    if (FAILED(CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED))) return 2;
    const wchar_t *browser = argc == 3 ? argv[1] : nullptr;
    const wchar_t *data = argc == 3 ? argv[2] : L"C:\\RuntimeQA\\WebViewData";
    if (argc == 1 && !_wfreopen(L"C:\\RuntimeQA\\webview2-result.txt", L"w", stdout)) return 2;
    window = CreateWindowExW(0, L"STATIC", L"WebView2 QA", WS_OVERLAPPEDWINDOW | WS_VISIBLE,
            0, 0, 640, 480, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!window) return 3;
    HMODULE loader = LoadLibraryW(L"WebView2Loader.dll");
    if (!loader) return 4;
    auto create = reinterpret_cast<decltype(&CreateCoreWebView2EnvironmentWithOptions)>(
            GetProcAddress(loader, "CreateCoreWebView2EnvironmentWithOptions"));
    if (!create) return 5;
    auto ready = new EnvironmentReady();
    HRESULT result = create(browser, data, nullptr, ready);
    ready->Release();
    if (FAILED(result)) callbackResult = result;
    int status = 1;
    for (ULONGLONG deadline = GetTickCount64() + 65000;
            GetTickCount64() < deadline && (callbackResult == E_PENDING || SUCCEEDED(callbackResult));) {
        MSG message;
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) DispatchMessageW(&message);
        PWSTR title = nullptr;
        if (view && SUCCEEDED(view->get_DocumentTitle(&title)) && title) {
            if (wcscmp(title, L"WinDroid WebView2 42") == 0) {
                printf("Title=%ls\n", title);
                status = 0;
            }
            CoTaskMemFree(title);
        }
        if (!status) break;
        Sleep(25);
    }
    printf("WebView2 callback=0x%08lx\n", static_cast<unsigned long>(callbackResult));
    if (view) view->Release();
    if (controller) { controller->Close(); controller->Release(); }
    DestroyWindow(window);
    CoUninitialize();
    return status;
}
