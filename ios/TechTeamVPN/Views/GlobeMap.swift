import MapKit
import SwiftUI

/// Satellite globe with two pins: where the phone really is and the selected server. While
/// connected a line joins them and the camera turns to the server, where sites now see you.
struct GlobeMap: UIViewRepresentable {
    var home: GeoIP.Location?
    var server: GeoIP.Location?
    var serverName: String
    var connected: Bool

    /// Far enough out that MapKit draws the whole globe.
    private static let distance: CLLocationDistance = 9_000_000

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.mapType = .hybridFlyover
        map.showsCompass = false
        map.showsScale = false
        map.pointOfInterestFilter = .excludingAll
        map.delegate = context.coordinator
        map.backgroundColor = .black
        map.camera = MKMapCamera(
            lookingAtCenter: CLLocationCoordinate2D(latitude: 55.75, longitude: 37.62),
            fromDistance: Self.distance, pitch: 0, heading: 0
        )
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        let c = context.coordinator
        c.connected = connected

        var pins: [Pin] = []
        if let home { pins.append(Pin(kind: .home, location: home, title: connected ? "Реальный адрес" : "Вы здесь")) }
        if let server { pins.append(Pin(kind: .server, location: server, title: connected ? "Вы сейчас здесь" : serverName)) }
        if pins != c.pins {
            map.removeAnnotations(map.annotations)
            map.addAnnotations(pins)
            c.pins = pins
        }

        // The route is redrawn only when it changes, so its flowing animation is not restarted.
        let route = connected ? home.flatMap { h in server.map { (h, $0) } } : nil
        let routeKey = route.map { "\($0.0.latitude),\($0.0.longitude)>\($0.1.latitude),\($0.1.longitude)" }
        if routeKey != c.routeKey {
            c.routeKey = routeKey
            map.removeOverlays(map.overlays)
            if case let (from, to)? = route {
                var points = [from.coordinate, to.coordinate]
                map.addOverlay(MKGeodesicPolyline(coordinates: &points, count: 2))
            }
        }

        // Turn the globe only when the place to look at changes, not on every redraw.
        let target = (connected ? server ?? home : home ?? server)?.coordinate
        if let target, c.target.map({ $0.latitude != target.latitude || $0.longitude != target.longitude }) ?? true {
            c.target = target
            // Look a little south of the point, so it sits in the upper half above the panel.
            let center = CLLocationCoordinate2D(latitude: max(-80, target.latitude - 14), longitude: target.longitude)
            let camera = MKMapCamera(lookingAtCenter: center, fromDistance: Self.distance, pitch: 0, heading: 0)
            map.setCamera(camera, animated: true)
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var pins: [Pin] = []
        var target: CLLocationCoordinate2D?
        var connected = false
        var routeKey: String?
        private weak var routeRenderer: MKPolylineRenderer?
        private var displayLink: CADisplayLink?
        /// One dash plus one gap; the dash phase cycles through it.
        private let dashPeriod: CGFloat = 26
        private var phase: CGFloat = 0

        deinit { displayLink?.invalidate() }

        /// Moves the dashes from the phone towards the server, like traffic going out.
        @objc private func step() {
            guard let renderer = routeRenderer else {
                displayLink?.invalidate()
                displayLink = nil
                return
            }
            // A smaller phase shifts the dash pattern forward along the line.
            phase = (phase - 1.2).truncatingRemainder(dividingBy: dashPeriod)
            renderer.lineDashPhase = phase < 0 ? phase + dashPeriod : phase
            renderer.setNeedsDisplay()
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            guard let pin = annotation as? Pin else { return nil }
            let view = MKMarkerAnnotationView(annotation: pin, reuseIdentifier: nil)
            view.glyphText = pin.location.flag
            view.titleVisibility = .visible
            view.displayPriority = .required
            switch pin.kind {
            case .home: view.markerTintColor = connected ? .systemGray : UIColor(Brand.cyan)
            case .server: view.markerTintColor = connected ? UIColor(Brand.green) : .systemGray
            }
            return view
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            let renderer = MKPolylineRenderer(overlay: overlay)
            renderer.strokeColor = UIColor(Brand.cyan)
            renderer.lineWidth = 4
            renderer.lineCap = .round
            renderer.lineDashPattern = [14, 12]
            routeRenderer = renderer
            if displayLink == nil {
                let link = CADisplayLink(target: self, selector: #selector(step))
                link.preferredFramesPerSecond = 30
                link.add(to: .main, forMode: .common)
                displayLink = link
            }
            return renderer
        }
    }

    final class Pin: NSObject, MKAnnotation {
        enum Kind { case home, server }
        let kind: Kind
        let location: GeoIP.Location
        let title: String?

        init(kind: Kind, location: GeoIP.Location, title: String) {
            self.kind = kind
            self.location = location
            self.title = title
        }

        var coordinate: CLLocationCoordinate2D { location.coordinate }

        override func isEqual(_ object: Any?) -> Bool {
            guard let other = object as? Pin else { return false }
            return kind == other.kind && location == other.location && title == other.title
        }

        override var hash: Int { title?.hashValue ?? 0 }
    }
}

extension GeoIP.Location {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }
}
