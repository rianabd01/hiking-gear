import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        IosMapBridgeRegistry.shared.bridge = MapLibreBridge.shared
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
