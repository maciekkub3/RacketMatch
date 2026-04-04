package com.racketmatch.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.racketmatch.domain.model.Court
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.darwin.NSObject

private class CourtAnnotation(val court: Court) : MKPointAnnotation()

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    onCourtTap: (Court) -> Unit,
    isDark: Boolean
) {
    val onCourtTapRef = rememberUpdatedState(onCourtTap)

    val delegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {
            override fun mapView(
                mapView: platform.MapKit.MKMapView,
                didSelectAnnotation: platform.MapKit.MKAnnotationProtocol
            ) {
                val annotation = didSelectAnnotation as? CourtAnnotation ?: return
                mapView.deselectAnnotation(annotation, animated = true)
                onCourtTapRef.value(annotation.court)
            }
        }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                showsUserLocation = false
                val center = CLLocationCoordinate2DMake(52.2297, 21.0122)
                setRegion(MKCoordinateRegionMakeWithDistance(center, 12000.0, 12000.0), animated = false)
            }
        },
        update = { mapView ->
            mapView.removeAnnotations(mapView.annotations)
            courts.forEach { court ->
                val annotation = CourtAnnotation(court).apply {
                    coordinate = CLLocationCoordinate2DMake(court.lat, court.lng)
                    title = court.name
                    subtitle = "${sessionCountByCourt[court.id] ?: 0} sesji"
                }
                mapView.addAnnotation(annotation)
            }
        }
    )
}
