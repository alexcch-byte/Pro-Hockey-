// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "PowerPlayHockeyCore",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [
        .library(name: "PowerPlayHockeyCore", targets: ["PowerPlayHockeyCore"])
    ],
    targets: [
        .target(name: "PowerPlayHockeyCore")
    ]
)
