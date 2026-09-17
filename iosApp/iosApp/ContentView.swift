import SwiftUI
import UIKit
import ComposeApp

struct ComposeViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeViewControllerRepresentable()
            .ignoresSafeArea(.keyboard) // Compose handles its own insets
    }
}
