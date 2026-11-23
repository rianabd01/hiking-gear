import ComposeApp
import CoreLocation
import Foundation
import MapLibre
import UIKit

final class MapLibreBridge: NSObject, IosNativeMapBridge {
    static let shared = MapLibreBridge()
    static let standardStyleURL = URL(string: "https://tiles.openfreemap.org/styles/liberty")!
    static let darkStyleURL = URL(string: "https://tiles.openfreemap.org/styles/dark")!

    static func styleURL(for modeId: String) -> URL {
        switch modeId {
        case "topo":
            return ensureRasterStyleFile(
                fileName: "hiking_style_topo.json",
                name: "Topografi",
                tiles: [
                    "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                    "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                    "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
                ],
                maxZoom: 17,
                attribution: "© OpenTopoMap"
            ) ?? standardStyleURL
        case "satellite":
            return ensureRasterStyleFile(
                fileName: "hiking_style_satellite.json",
                name: "Satelit",
                tiles: [
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                ],
                maxZoom: 18,
                attribution: "© Esri"
            ) ?? standardStyleURL
        case "dark":
            return darkStyleURL
        default:
            return standardStyleURL
        }
    }

    private static func ensureRasterStyleFile(
        fileName: String,
        name: String,
        tiles: [String],
        maxZoom: Int,
        attribution: String
    ) -> URL? {
        guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        let styleDir = appSupport.appendingPathComponent("MapStyles", isDirectory: true)
        try? FileManager.default.createDirectory(at: styleDir, withIntermediateDirectories: true)
        let fileURL = styleDir.appendingPathComponent(fileName)
        let styleDict: [String: Any] = [
            "version": 8,
            "name": name,
            "sources": [
                "raster-tiles": [
                    "type": "raster",
                    "tiles": tiles,
                    "tileSize": 256,
                    "maxzoom": maxZoom,
                    "attribution": attribution
                ]
            ],
            "layers": [
                [
                    "id": "raster-layer",
                    "type": "raster",
                    "source": "raster-tiles"
                ]
            ]
        ]
        if let data = try? JSONSerialization.data(withJSONObject: styleDict, options: []) {
            try? data.write(to: fileURL, options: .atomic)
            return fileURL
        }
        return nil
    }

    private var observersRegistered = false
    private var packsObservation: NSKeyValueObservation?
    private var activeSnapshotter: MLNMapSnapshotter?

    private override init() {
        super.init()
        MLNOfflineStorage.shared.setMaximumAllowedMapboxTiles(100_000)
        registerOfflineObservers()
    }

    func createMapView() -> UIView {
        HikingMapLibreContainerView(styleURL: Self.standardStyleURL)
    }

    func updateMapView(
        view: UIView,
        trackLines: [[LatLon]],
        recordedLines: [[LatLon]],
        pins: [NativeMapPin],
        bounds: Bounds?,
        showUserLocation: Bool,
        liveLocation: LiveLocation?,
        styleModeId: String,
        recenterTrigger: Int32
    ) {
        guard let container = view as? HikingMapLibreContainerView else { return }
        container.applyUpdate(
            trackLines: trackLines,
            recordedLines: recordedLines,
            pins: pins,
            bounds: bounds,
            showUserLocation: showUserLocation,
            liveLocation: liveLocation,
            styleModeId: styleModeId,
            recenterTrigger: Int(recenterTrigger)
        )
    }

    func refreshOfflineStates() {
        DispatchQueue.main.async {
            guard let packs = MLNOfflineStorage.shared.packs else { return }
            let registry = IosMapBridgeRegistry.shared
            registry.clearAllStates()
            for pack in packs {
                if pack.state == .unknown {
                    pack.requestProgress()
                } else {
                    self.publishPackState(pack)
                }
            }
        }
    }

    func startOfflineDownload(
        regionId: String,
        bounds: Bounds,
        minZoom: Double,
        maxZoom: Double,
        styleModeId: String
    ) {
        DispatchQueue.main.async {
            if let existing = self.findPack(regionId: regionId) {
                if existing.state == .complete {
                    self.publishPackState(existing)
                    return
                }
                existing.resume()
                self.publishPackState(existing)
                return
            }

            let mlnBounds = MLNCoordinateBounds(
                sw: CLLocationCoordinate2D(latitude: bounds.minLat, longitude: bounds.minLon),
                ne: CLLocationCoordinate2D(latitude: bounds.maxLat, longitude: bounds.maxLon)
            )
            let region = MLNTilePyramidOfflineRegion(
                styleURL: Self.styleURL(for: styleModeId),
                bounds: mlnBounds,
                fromZoomLevel: minZoom,
                toZoomLevel: maxZoom
            )
            let contextData = (try? JSONSerialization.data(withJSONObject: ["regionId": regionId])) ?? Data()

            MLNOfflineStorage.shared.addPack(for: region, withContext: contextData) { pack, error in
                DispatchQueue.main.async {
                    if let error = error {
                        IosMapBridgeRegistry.shared.notifyError(
                            regionId: regionId,
                            message: error.localizedDescription
                        )
                        return
                    }
                    guard let pack = pack else {
                        IosMapBridgeRegistry.shared.notifyError(
                            regionId: regionId,
                            message: "Gagal membuat paket offline"
                        )
                        return
                    }
                    pack.resume()
                    self.publishPackState(pack)
                }
            }
        }
    }

    func cancelOfflineDownload(regionId: String) {
        DispatchQueue.main.async {
            guard let pack = self.findPack(regionId: regionId) else {
                IosMapBridgeRegistry.shared.notifyNotDownloaded(regionId: regionId)
                return
            }
            pack.suspend()
            MLNOfflineStorage.shared.removePack(pack) { _ in
                DispatchQueue.main.async {
                    IosMapBridgeRegistry.shared.notifyNotDownloaded(regionId: regionId)
                }
            }
        }
    }

    func deleteOfflineDownload(regionId: String) {
        DispatchQueue.main.async {
            let prefix = "\(regionId)::"
            let matching = (MLNOfflineStorage.shared.packs ?? []).filter { pack in
                guard let key = Self.regionId(from: pack.context) else { return false }
                return key == regionId || key.hasPrefix(prefix)
            }
            if matching.isEmpty {
                IosMapBridgeRegistry.shared.notifyNotDownloaded(regionId: regionId)
                return
            }
            for pack in matching {
                let packKey = Self.regionId(from: pack.context) ?? regionId
                pack.suspend()
                MLNOfflineStorage.shared.removePack(pack) { _ in
                    DispatchQueue.main.async {
                        IosMapBridgeRegistry.shared.notifyNotDownloaded(regionId: packKey)
                    }
                }
            }
        }
    }

    func shareActivityCard(
        title: String,
        subtitle: String?,
        trackLines: [[LatLon]],
        bounds: Bounds?,
        distanceText: String,
        durationText: String,
        elevationGainText: String,
        maxElevationText: String,
        styleModeId: String,
        modelId: String,
        languageCode: String,
        shareText: String,
        onComplete: @escaping () -> Void
    ) {
        DispatchQueue.main.async {
            let needsMap = (modelId == "map_card" || modelId == "story_poster")
            let mapSize = modelId == "story_poster"
                ? CGSize(width: 540, height: 960)
                : CGSize(width: 540, height: 405)

            func finishWith(snapshot: MLNMapSnapshot?) {
                let image = Self.renderActivityShareImage(
                    snapshot: snapshot,
                    modelId: modelId,
                    title: title,
                    subtitle: subtitle,
                    trackLines: trackLines,
                    bounds: bounds,
                    distanceText: distanceText,
                    durationText: durationText,
                    elevationGainText: elevationGainText,
                    maxElevationText: maxElevationText,
                    languageCode: languageCode
                )
                self.presentShareSheet(image: image, transparent: modelId == "transparent", shareText: shareText)
                onComplete()
            }

            guard needsMap, let b = bounds else {
                finishWith(snapshot: nil)
                return
            }

            let latSpan = max(abs(b.maxLat - b.minLat), 0.0005)
            let lonSpan = max(abs(b.maxLon - b.minLon), 0.0005)
            let latPad = latSpan * (modelId == "story_poster" ? 0.45 : 0.22)
            let lonPad = lonSpan * 0.22
            let paddedBounds = MLNCoordinateBounds(
                sw: CLLocationCoordinate2D(latitude: b.minLat - latPad, longitude: b.minLon - lonPad),
                ne: CLLocationCoordinate2D(latitude: b.maxLat + latPad, longitude: b.maxLon + lonPad)
            )
            let center = CLLocationCoordinate2D(
                latitude: (b.minLat + b.maxLat) / 2.0,
                longitude: (b.minLon + b.maxLon) / 2.0
            )
            let camera = MLNMapCamera(lookingAtCenter: center, altitude: 5000, pitch: 0, heading: 0)
            let options = MLNMapSnapshotOptions(styleURL: Self.styleURL(for: styleModeId), camera: camera, size: mapSize)
            options.coordinateBounds = paddedBounds
            options.scale = 2.0
            options.showsLogo = false

            var finished = false
            let snapshotter = MLNMapSnapshotter(options: options)
            self.activeSnapshotter = snapshotter

            DispatchQueue.main.asyncAfter(deadline: .now() + 4.0) { [weak self] in
                guard !finished else { return }
                finished = true
                self?.activeSnapshotter?.cancel()
                self?.activeSnapshotter = nil
                finishWith(snapshot: nil)
            }

            snapshotter.start { [weak self] snapshot, _ in
                DispatchQueue.main.async {
                    guard !finished else { return }
                    finished = true
                    self?.activeSnapshotter = nil
                    finishWith(snapshot: snapshot)
                }
            }
        }
    }

    private func presentShareSheet(image: UIImage, transparent: Bool, shareText: String) {
        guard let vc = Self.topViewController() else { return }
        var items: [Any] = [image, shareText]
        if transparent, let pngData = image.pngData() {
            let tmpURL = FileManager.default.temporaryDirectory.appendingPathComponent("hiking-gear-rute.png")
            if (try? pngData.write(to: tmpURL, options: .atomic)) != nil {
                items = [tmpURL, shareText]
            }
        }
        let activityVC = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = vc.view
            popover.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        vc.present(activityVC, animated: true)
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow })
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }

    private static func renderActivityShareImage(
        snapshot: MLNMapSnapshot?,
        modelId: String,
        title: String,
        subtitle: String?,
        trackLines: [[LatLon]],
        bounds: Bounds?,
        distanceText: String,
        durationText: String,
        elevationGainText: String,
        maxElevationText: String,
        languageCode: String
    ) -> UIImage {
        let isIndonesian = (languageCode != "en")
        let isStory = modelId == "story_poster"
        let isMinimal = modelId == "route_minimal"
        let isTransparent = modelId == "transparent"
        let cardSize = isStory ? CGSize(width: 540, height: 960) : CGSize(width: 540, height: 675)
        let mapRect: CGRect = if isStory {
            CGRect(origin: .zero, size: cardSize)
        } else if isMinimal || isTransparent {
            CGRect(x: 36, y: 76, width: 468, height: 372)
        } else {
            CGRect(x: 0, y: 0, width: 540, height: 405)
        }

        let bgColor = UIColor(red: 12 / 255, green: 14 / 255, blue: 11 / 255, alpha: 1)
        let panelColor = UIColor(red: 20 / 255, green: 24 / 255, blue: 18 / 255, alpha: 1)
        let stabilo = UIColor(red: 217 / 255, green: 1, blue: 0, alpha: 1)
        let mutedText = isTransparent
            ? UIColor(white: 0.92, alpha: 0.88)
            : UIColor(red: 162 / 255, green: 171 / 255, blue: 150 / 255, alpha: 1)
        let dividerColor = UIColor(red: 46 / 255, green: 53 / 255, blue: 40 / 255, alpha: 1)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = !isTransparent

        return UIGraphicsImageRenderer(size: cardSize, format: format).image { ctx in
            let cg = ctx.cgContext
            if !isTransparent {
                bgColor.setFill()
                cg.fill(CGRect(origin: .zero, size: cardSize))
            } else {
                cg.clear(CGRect(origin: .zero, size: cardSize))
            }

            // 1. Map or grid canvas
            cg.saveGState()
            if !isTransparent {
                cg.clip(to: isStory ? CGRect(origin: .zero, size: cardSize) : mapRect)
                if let snap = snapshot {
                    snap.image.draw(in: mapRect)
                } else {
                    if !isMinimal {
                        panelColor.setFill()
                        cg.fill(mapRect)
                    }
                    dividerColor.withAlphaComponent(isMinimal ? 0.22 : 0.35).setStroke()
                    cg.setLineWidth(1.0)
                    for x in stride(from: mapRect.minX + 36.0, to: mapRect.maxX, by: 36.0) {
                        cg.move(to: CGPoint(x: x, y: mapRect.minY))
                        cg.addLine(to: CGPoint(x: x, y: mapRect.maxY))
                    }
                    for y in stride(from: mapRect.minY + 36.0, to: mapRect.maxY, by: 36.0) {
                        cg.move(to: CGPoint(x: mapRect.minX, y: y))
                        cg.addLine(to: CGPoint(x: mapRect.maxX, y: y))
                    }
                    cg.strokePath()
                }
            }

            // Project route lines
            let projectedLines: [[CGPoint]] = trackLines.compactMap { line in
                guard line.count >= 2 else { return nil }
                if let snap = snapshot {
                    return line.map { snap.point(for: CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon)) }
                } else {
                    let inset = (isMinimal || isTransparent) ? mapRect.insetBy(dx: 24, dy: 24) : mapRect.insetBy(dx: 48, dy: 44)
                    return Self.projectFallbackLine(line, allBounds: bounds, rect: inset)
                }
            }

            if isTransparent {
                cg.setShadow(offset: CGSize(width: 0, height: 2), blur: 6, color: UIColor.black.withAlphaComponent(0.7).cgColor)
            }

            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            for pts in projectedLines where pts.count >= 2 {
                if !isTransparent {
                    cg.setStrokeColor(bgColor.withAlphaComponent(0.85).cgColor)
                    cg.setLineWidth(isMinimal ? 7.5 : 6.5)
                    cg.addLines(between: pts)
                    cg.strokePath()
                }

                let lineColor = snapshot != nil
                    ? UIColor(red: 1.0, green: 107 / 255, blue: 0, alpha: 1.0)
                    : stabilo
                cg.setStrokeColor(lineColor.cgColor)
                cg.setLineWidth(isMinimal || isTransparent ? 4.5 : 3.8)
                cg.addLines(between: pts)
                cg.strokePath()
            }

            if let firstPt = projectedLines.first?.first, let lastPt = projectedLines.last?.last {
                let startDot = CGRect(x: firstPt.x - 5.5, y: firstPt.y - 5.5, width: 11, height: 11)
                let endDot = CGRect(x: lastPt.x - 5.5, y: lastPt.y - 5.5, width: 11, height: 11)
                UIColor(red: 74 / 255, green: 222 / 255, blue: 128 / 255, alpha: 1).setFill()
                cg.fillEllipse(in: startDot)
                UIColor.systemRed.setFill()
                cg.fillEllipse(in: endDot)
                if !isTransparent {
                    bgColor.setStroke()
                    cg.setLineWidth(2)
                    cg.strokeEllipse(in: startDot)
                    cg.strokeEllipse(in: endDot)
                }
            }

            // Gradients
            if isStory {
                let topColors = [bgColor.withAlphaComponent(0.88).cgColor, bgColor.withAlphaComponent(0).cgColor] as CFArray
                if let topGrad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: topColors, locations: [0.0, 1.0]) {
                    cg.drawLinearGradient(topGrad, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: 210), options: [])
                }
                let botColors = [bgColor.withAlphaComponent(0).cgColor, bgColor.withAlphaComponent(0.94).cgColor] as CFArray
                if let botGrad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: botColors, locations: [0.0, 1.0]) {
                    cg.drawLinearGradient(botGrad, start: CGPoint(x: 0, y: cardSize.height - 280), end: CGPoint(x: 0, y: cardSize.height), options: [])
                }
            } else if !isMinimal && !isTransparent {
                let colors = [bgColor.withAlphaComponent(0).cgColor, bgColor.cgColor] as CFArray
                if let grad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0.0, 1.0]) {
                    cg.drawLinearGradient(
                        grad,
                        start: CGPoint(x: 0, y: mapRect.maxY - 68),
                        end: CGPoint(x: 0, y: mapRect.maxY),
                        options: []
                    )
                }
            }
            cg.restoreGState()

            // 2. Text & stats overlay/panel
            let padX: CGFloat = 28
            let contentW = cardSize.width - padX * 2
            let titleStyle = NSMutableParagraphStyle()
            titleStyle.lineBreakMode = .byTruncatingTail

            if isTransparent {
                cg.saveGState()
                cg.setShadow(offset: CGSize(width: 0, height: 2), blur: 6, color: UIColor.black.withAlphaComponent(0.75).cgColor)
            }

            let brandAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 12, weight: .bold),
                .foregroundColor: stabilo,
                .kern: 1.4
            ]
            let titleAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 25, weight: .bold),
                .foregroundColor: UIColor.white,
                .paragraphStyle: titleStyle
            ]

            var cursorY: CGFloat = if isStory {
                56
            } else if isMinimal || isTransparent {
                474
            } else {
                mapRect.maxY + 14
            }

            let brandText = isIndonesian
                ? "HIKING GEAR  •  AKTIVITAS PENDAKIAN"
                : "HIKING GEAR  •  HIKING ACTIVITY"
            if isMinimal || isTransparent {
                NSString(string: brandText).draw(
                    in: CGRect(x: padX, y: 34, width: contentW, height: 18),
                    withAttributes: brandAttrs
                )
            } else {
                NSString(string: brandText).draw(
                    in: CGRect(x: padX, y: cursorY, width: contentW, height: 18),
                    withAttributes: brandAttrs
                )
                cursorY += 24
            }

            let titleRect = CGRect(x: padX, y: cursorY, width: contentW, height: 64)
            let measuredTitle = NSString(string: title).boundingRect(
                with: CGSize(width: contentW, height: 64),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: titleAttrs,
                context: nil
            )
            NSString(string: title).draw(in: titleRect, withAttributes: titleAttrs)
            cursorY += min(ceil(measuredTitle.height), 64) + 6

            if let sub = subtitle, !sub.isEmpty {
                let subAttrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 14, weight: .regular),
                    .foregroundColor: mutedText,
                    .paragraphStyle: titleStyle
                ]
                NSString(string: sub).draw(
                    in: CGRect(x: padX, y: cursorY, width: contentW, height: 38),
                    withAttributes: subAttrs
                )
            }

            let statsY: CGFloat = cardSize.height - (isStory ? 116 : 96)
            if !isTransparent {
                dividerColor.setFill()
                cg.fill(CGRect(x: padX, y: statsY - 16, width: contentW, height: 1))
            }

            let stats: [(String, String, Bool)] = isIndonesian ? [
                ("JARAK", distanceText, true),
                ("DURASI", durationText, true),
                ("KENAIKAN ELEV.", elevationGainText, false),
                ("ELEV. MAKS", maxElevationText, false)
            ] : [
                ("DISTANCE", distanceText, true),
                ("DURATION", durationText, true),
                ("ELEV. GAIN", elevationGainText, false),
                ("MAX ELEV.", maxElevationText, false)
            ]
            let colW = contentW / CGFloat(stats.count)
            for (idx, item) in stats.enumerated() {
                let x = padX + CGFloat(idx) * colW
                let labelAttrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 10.5, weight: .semibold),
                    .foregroundColor: mutedText,
                    .kern: 0.8
                ]
                let valAttrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.monospacedDigitSystemFont(ofSize: 19, weight: .bold),
                    .foregroundColor: item.2 ? stabilo : UIColor.white
                ]
                NSString(string: item.0).draw(
                    in: CGRect(x: x, y: statsY, width: colW - 6, height: 16),
                    withAttributes: labelAttrs
                )
                NSString(string: item.1).draw(
                    in: CGRect(x: x, y: statsY + 20, width: colW - 6, height: 28),
                    withAttributes: valAttrs
                )
            }

            if isTransparent {
                cg.restoreGState()
            }
        }
    }

    private static func projectFallbackLine(_ line: [LatLon], allBounds: Bounds?, rect: CGRect) -> [CGPoint] {
        guard let b = allBounds else { return [] }
        let midLatRad = ((b.minLat + b.maxLat) / 2.0) * Double.pi / 180.0
        let cosLat = max(cos(midLatRad), 0.1)
        let spanX = max((b.maxLon - b.minLon) * cosLat, 1e-6)
        let spanY = max(b.maxLat - b.minLat, 1e-6)
        let scale = min(Double(rect.width) / spanX, Double(rect.height) / spanY)
        let drawW = spanX * scale
        let drawH = spanY * scale
        let offsetX = Double(rect.minX) + (Double(rect.width) - drawW) / 2.0
        let offsetY = Double(rect.minY) + (Double(rect.height) - drawH) / 2.0
        return line.map { pt in
            let x = offsetX + (pt.lon - b.minLon) * cosLat * scale
            let y = offsetY + (b.maxLat - pt.lat) * scale
            return CGPoint(x: x, y: y)
        }
    }

    private func registerOfflineObservers() {
        guard !observersRegistered else { return }
        observersRegistered = true

        packsObservation = MLNOfflineStorage.shared.observe(\.packs, options: [.initial, .new]) { [weak self] _, _ in
            self?.refreshOfflineStates()
        }

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePackProgress(_:)),
            name: .MLNOfflinePackProgressChanged,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePackError(_:)),
            name: .MLNOfflinePackError,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleTileLimit(_:)),
            name: .MLNOfflinePackMaximumMapboxTilesReached,
            object: nil
        )
    }

    @objc private func handlePackProgress(_ notification: Notification) {
        guard let pack = notification.object as? MLNOfflinePack else { return }
        DispatchQueue.main.async {
            self.publishPackState(pack)
        }
    }

    @objc private func handlePackError(_ notification: Notification) {
        guard let pack = notification.object as? MLNOfflinePack,
              let regionId = Self.regionId(from: pack.context) else { return }
        let err = notification.userInfo?[MLNOfflinePackUserInfoKey.error] as? NSError
        DispatchQueue.main.async {
            pack.suspend()
            IosMapBridgeRegistry.shared.notifyError(
                regionId: regionId,
                message: err?.localizedDescription ?? "Kesalahan jaringan saat unduh"
            )
        }
    }

    @objc private func handleTileLimit(_ notification: Notification) {
        guard let pack = notification.object as? MLNOfflinePack,
              let regionId = Self.regionId(from: pack.context) else { return }
        DispatchQueue.main.async {
            pack.suspend()
            IosMapBridgeRegistry.shared.notifyError(
                regionId: regionId,
                message: "Melebihi batas jumlah tile offline"
            )
        }
    }

    private func publishPackState(_ pack: MLNOfflinePack) {
        guard let regionId = Self.regionId(from: pack.context) else { return }
        let progress = pack.progress
        let registry = IosMapBridgeRegistry.shared
        switch pack.state {
        case .complete:
            registry.notifyDownloaded(
                regionId: regionId,
                bytes: Int64(progress.countOfBytesCompleted)
            )
        case .active:
            registry.notifyDownloading(
                regionId: regionId,
                completed: Int64(progress.countOfResourcesCompleted),
                expected: Int64(progress.countOfResourcesExpected),
                bytes: Int64(progress.countOfBytesCompleted)
            )
        case .inactive:
            if progress.countOfResourcesCompleted > 0 &&
                progress.countOfResourcesCompleted >= progress.countOfResourcesExpected &&
                progress.countOfResourcesExpected > 0 {
                registry.notifyDownloaded(
                    regionId: regionId,
                    bytes: Int64(progress.countOfBytesCompleted)
                )
            } else {
                registry.notifyNotDownloaded(regionId: regionId)
            }
        case .unknown:
            pack.requestProgress()
        case .invalid:
            break
        @unknown default:
            break
        }
    }

    private func findPack(regionId: String) -> MLNOfflinePack? {
        (MLNOfflineStorage.shared.packs ?? []).first {
            Self.regionId(from: $0.context) == regionId
        }
    }

    private static func regionId(from context: Data) -> String? {
        guard let obj = try? JSONSerialization.jsonObject(with: context) as? [String: Any] else {
            return nil
        }
        return obj["regionId"] as? String
    }
}

private final class HikingMapLibreContainerView: UIView, MLNMapViewDelegate {
    private let mapView: MLNMapView
    private var pendingTrackLines: [[LatLon]] = []
    private var pendingRecordedLines: [[LatLon]] = []
    private var pendingPins: [NativeMapPin] = []
    private var pendingBounds: Bounds?
    private var pendingLiveLocation: LiveLocation?
    private var activeAnnotations: [MLNPointAnnotation] = []

    private var lastTrackSignature: String = ""
    private var lastRecordedSignature: String = ""
    private var lastPinsSignature: String = ""
    private var appliedBoundsSignature: String = ""
    private var hasCenteredOnUser = false
    private var isStyleLoaded = false
    private var currentStyleModeId = "standard"
    private var lastRecenterTrigger = 0

    private let gpxSourceId = "hiking-gpx-source"
    private let gpxLayerId = "hiking-gpx-layer"
    private let recordedSourceId = "hiking-recorded-source"
    private let recordedLayerId = "hiking-recorded-layer"

    init(styleURL: URL) {
        let initialFrame = CGRect(x: 0, y: 0, width: 390, height: 640)
        self.mapView = MLNMapView(frame: initialFrame, styleURL: styleURL)
        super.init(frame: initialFrame)

        mapView.frame = bounds
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mapView.automaticallyAdjustsContentInset = false
        mapView.delegate = self
        mapView.showsUserLocation = true
        mapView.compassView.compassVisibility = .adaptive
        addSubview(mapView)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            DispatchQueue.main.async { [weak self] in
                self?.applyPendingCameraIfReady()
            }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if mapView.frame != bounds {
            mapView.frame = bounds
        }
        mapView.layoutIfNeeded()
        applyPendingCameraIfReady()
    }

    func applyUpdate(
        trackLines: [[LatLon]],
        recordedLines: [[LatLon]],
        pins: [NativeMapPin],
        bounds: Bounds?,
        showUserLocation: Bool,
        liveLocation: LiveLocation?,
        styleModeId: String,
        recenterTrigger: Int
    ) {
        if styleModeId != currentStyleModeId {
            currentStyleModeId = styleModeId
            isStyleLoaded = false
            mapView.styleURL = MapLibreBridge.styleURL(for: styleModeId)
        }

        if mapView.showsUserLocation != showUserLocation {
            mapView.showsUserLocation = showUserLocation
        }

        let trackSig = Self.linesSignature(trackLines)
        if trackSig != lastTrackSignature {
            lastTrackSignature = trackSig
            pendingTrackLines = trackLines
            if let style = mapView.style {
                syncLineLayer(
                    style: style,
                    sourceId: gpxSourceId,
                    layerId: gpxLayerId,
                    lines: pendingTrackLines,
                    color: .systemOrange,
                    width: 3.5
                )
            }
        }

        let recSig = Self.linesSignature(recordedLines)
        if recSig != lastRecordedSignature {
            lastRecordedSignature = recSig
            pendingRecordedLines = recordedLines
            if let style = mapView.style {
                syncLineLayer(
                    style: style,
                    sourceId: recordedSourceId,
                    layerId: recordedLayerId,
                    lines: pendingRecordedLines,
                    color: .systemRed,
                    width: 4.0
                )
            }
        }

        let pinSig = Self.pinsSignature(pins)
        if pinSig != lastPinsSignature {
            lastPinsSignature = pinSig
            pendingPins = pins
            syncPins()
        }

        pendingBounds = bounds
        pendingLiveLocation = liveLocation
        applyPendingCameraIfReady()

        if recenterTrigger != lastRecenterTrigger {
            lastRecenterTrigger = recenterTrigger
            if recenterTrigger > 0 {
                let targetCoord: CLLocationCoordinate2D? = liveLocation.map {
                    CLLocationCoordinate2D(latitude: $0.coord.lat, longitude: $0.coord.lon)
                } ?? mapView.userLocation?.location?.coordinate
                if let coord = targetCoord {
                    let targetZoom = max(mapView.zoomLevel, 15.0)
                    mapView.setCenter(coord, zoomLevel: targetZoom, animated: true)
                    hasCenteredOnUser = true
                }
            }
        }
    }

    private func applyPendingCameraIfReady() {
        guard window != nil, mapView.bounds.width > 120, mapView.bounds.height > 120 else { return }

        if let b = pendingBounds {
            let bSig = "\(b.minLat),\(b.minLon),\(b.maxLat),\(b.maxLon)"
            guard bSig != appliedBoundsSignature else { return }

            let center = CLLocationCoordinate2D(
                latitude: (b.minLat + b.maxLat) / 2.0,
                longitude: (b.minLon + b.maxLon) / 2.0
            )
            let fitZoom = Self.mercatorFitZoom(
                bounds: b,
                viewportSize: mapView.bounds.size,
                padding: 40.0
            )
            mapView.setCenter(center, zoomLevel: fitZoom, animated: false)

            if isStyleLoaded {
                let latSpan = abs(b.maxLat - b.minLat)
                let lonSpan = abs(b.maxLon - b.minLon)
                if latSpan >= 0.001 || lonSpan >= 0.001 {
                    let mlnBounds = MLNCoordinateBounds(
                        sw: CLLocationCoordinate2D(latitude: b.minLat, longitude: b.minLon),
                        ne: CLLocationCoordinate2D(latitude: b.maxLat, longitude: b.maxLon)
                    )
                    let edgeInsets = mapView.bounds.height < 420
                        ? UIEdgeInsets(top: 28, left: 28, bottom: 28, right: 28)
                        : UIEdgeInsets(top: 110, left: 36, bottom: 110, right: 36)
                    mapView.setVisibleCoordinateBounds(
                        mlnBounds,
                        edgePadding: edgeInsets,
                        animated: false,
                        completionHandler: nil
                    )
                    if mapView.zoomLevel < fitZoom - 1.5 {
                        mapView.setCenter(center, zoomLevel: fitZoom, animated: false)
                    }
                }
                appliedBoundsSignature = bSig
                NSLog(
                    "[HikingMap] applied bounds=%@ size=%.0fx%.0f fitZoom=%.2f actualZoom=%.2f",
                    bSig,
                    mapView.bounds.width,
                    mapView.bounds.height,
                    fitZoom,
                    mapView.zoomLevel
                )
            }
        } else {
            guard !hasCenteredOnUser else { return }
            let coord: CLLocationCoordinate2D? = pendingLiveLocation.map {
                CLLocationCoordinate2D(latitude: $0.coord.lat, longitude: $0.coord.lon)
            } ?? mapView.userLocation?.location?.coordinate

            guard let c = coord else { return }
            mapView.setCenter(c, zoomLevel: 15.0, animated: false)
            if isStyleLoaded {
                hasCenteredOnUser = true
                NSLog("[HikingMap] centered on user zoom=%.2f", mapView.zoomLevel)
            }
        }
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        isStyleLoaded = true
        syncLineLayer(
            style: style,
            sourceId: gpxSourceId,
            layerId: gpxLayerId,
            lines: pendingTrackLines,
            color: .systemOrange,
            width: 3.5
        )
        syncLineLayer(
            style: style,
            sourceId: recordedSourceId,
            layerId: recordedLayerId,
            lines: pendingRecordedLines,
            color: .systemRed,
            width: 4.0
        )
        syncPins()
        applyPendingCameraIfReady()
        DispatchQueue.main.async { [weak self] in
            self?.applyPendingCameraIfReady()
        }
    }

    func mapView(_ mapView: MLNMapView, didUpdate userLocation: MLNUserLocation?) {
        if pendingBounds == nil && !hasCenteredOnUser {
            applyPendingCameraIfReady()
        }
    }

    func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool {
        true
    }

    private func syncLineLayer(
        style: MLNStyle,
        sourceId: String,
        layerId: String,
        lines: [[LatLon]],
        color: UIColor,
        width: Double
    ) {
        let features: [MLNPolylineFeature] = lines.compactMap { line in
            guard line.count >= 2 else { return nil }
            var coords = line.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
            return coords.withUnsafeMutableBufferPointer { buf in
                guard let ptr = buf.baseAddress else { return nil }
                return MLNPolylineFeature(coordinates: ptr, count: UInt(buf.count))
            }
        }
        let collection = MLNShapeCollectionFeature(shapes: features)

        if let existingSource = style.source(withIdentifier: sourceId) as? MLNShapeSource {
            existingSource.shape = collection
        } else {
            let source = MLNShapeSource(identifier: sourceId, shape: collection, options: nil)
            style.addSource(source)
            let layer = MLNLineStyleLayer(identifier: layerId, source: source)
            layer.lineColor = NSExpression(forConstantValue: color)
            layer.lineWidth = NSExpression(forConstantValue: width)
            layer.lineCap = NSExpression(forConstantValue: "round")
            layer.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(layer)
        }
    }

    private func syncPins() {
        if !activeAnnotations.isEmpty {
            mapView.removeAnnotations(activeAnnotations)
            activeAnnotations.removeAll()
        }
        let annotations = pendingPins.map { pin -> MLNPointAnnotation in
            let ann = MLNPointAnnotation()
            ann.coordinate = CLLocationCoordinate2D(latitude: pin.lat, longitude: pin.lon)
            ann.title = pin.title
            ann.subtitle = pin.subtitle
            return ann
        }
        if !annotations.isEmpty {
            mapView.addAnnotations(annotations)
            activeAnnotations.append(contentsOf: annotations)
        }
    }

    private static func mercatorFitZoom(bounds: Bounds, viewportSize: CGSize, padding: Double) -> Double {
        let latSpan = abs(bounds.maxLat - bounds.minLat)
        let lonSpan = abs(bounds.maxLon - bounds.minLon)
        if latSpan < 0.001 && lonSpan < 0.001 {
            return 15.0
        }

        func mercatorY(_ latDeg: Double) -> Double {
            let clamped = max(-85.0511, min(85.0511, latDeg))
            let rad = clamped * Double.pi / 180.0
            return (1.0 - log(tan(Double.pi / 4.0 + rad / 2.0)) / Double.pi) / 2.0
        }

        let dx = max(lonSpan / 360.0, 1e-6)
        let dy = max(abs(mercatorY(bounds.maxLat) - mercatorY(bounds.minLat)), 1e-6)
        let availW = max(Double(viewportSize.width) - padding * 2.0, 64.0)
        let availH = max(Double(viewportSize.height) - padding * 2.0, 64.0)

        let zoomX = log2(availW / (512.0 * dx))
        let zoomY = log2(availH / (512.0 * dy))
        return min(max(min(zoomX, zoomY), 2.0), 16.0)
    }

    private static func linesSignature(_ lines: [[LatLon]]) -> String {
        guard !lines.isEmpty else { return "" }
        return lines.map { line in
            let first = line.first.map { "\($0.lat),\($0.lon)" } ?? ""
            let last = line.last.map { "\($0.lat),\($0.lon)" } ?? ""
            return "\(line.count):\(first)->\(last)"
        }.joined(separator: "|")
    }

    private static func pinsSignature(_ pins: [NativeMapPin]) -> String {
        guard !pins.isEmpty else { return "" }
        return "\(pins.count):\(pins.first?.lat ?? 0),\(pins.first?.lon ?? 0):\(pins.last?.title ?? "")"
    }
}
