// swift-tools-version:5.3
import PackageDescription

let package = Package(
    name: "MapLibre",
    platforms: [.iOS(.v12)],
    products: [
        .library(name: "MapLibre", targets: ["MapLibre"])
    ],
    targets: [
        .binaryTarget(name: "MapLibre", path: "MapLibre.xcframework")
    ]
)
