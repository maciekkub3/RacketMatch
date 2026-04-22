package com.racketmatch.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.Sport
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKClusterAnnotation
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKUserLocation
import platform.UIKit.UIColor
import platform.darwin.NSObject

private const val CLUSTER_ID = "courts"

private class CourtAnnotation(val court: Court) : MKPointAnnotation()

private val CITY_CENTERS_IOS = mapOf(
    "Warszawa" to Pair(52.2297, 21.0122),
    "Poznań"   to Pair(52.4064, 16.9252),
    "Wrocław"  to Pair(51.1079, 17.0385),
    "Szczecin" to Pair(53.4285, 14.5528)
)

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    onCourtTap: (Court) -> Unit,
    city: String,
    isDark: Boolean
) {
    val onCourtTapRef = rememberUpdatedState(onCourtTap)
    val sessionCountRef = rememberUpdatedState(sessionCountByCourt)
    val isDarkRef = rememberUpdatedState(isDark)

    val delegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {

            override fun mapView(
                mapView: MKMapView,
                viewForAnnotation: MKAnnotationProtocol
            ): MKAnnotationView? {
                if (viewForAnnotation is MKUserLocation) return null
                val dark = isDarkRef.value

                val greenColor = if (dark)
                    UIColor(red = 0.165, green = 0.165, blue = 0.157, alpha = 1.0)
                else
                    UIColor(red = 0.0, green = 0.4, blue = 0.2, alpha = 1.0)
                val limeColor = UIColor(red = 0.619, green = 0.784, blue = 0.039, alpha = 1.0)

                if (viewForAnnotation is MKClusterAnnotation) {
                    val reuseId = "cluster"
                    val existing = mapView.dequeueReusableAnnotationViewWithIdentifier(reuseId)
                    val view = (existing as? MKMarkerAnnotationView)
                        ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = reuseId)
                    view.annotation = viewForAnnotation
                    view.canShowCallout = false

                    val members = viewForAnnotation.memberAnnotations.filterIsInstance<CourtAnnotation>()
                    val count = members.size
                    val hasSession = members.any { (sessionCountRef.value[it.court.id] ?: 0) > 0 }
                    val sports = members.flatMap { it.court.sports }.toSet()

                    // Show count as glyph; emoji suffix indicates sport mix
                    view.glyphText = when {
                        sports.contains(Sport.TENNIS) && sports.contains(Sport.PADEL) -> "$count 🎾🏸"
                        sports.contains(Sport.PADEL) -> "$count 🏸"
                        else -> "$count 🎾"
                    }
                    view.markerTintColor = if (hasSession) limeColor else greenColor
                    return view
                }

                if (viewForAnnotation is CourtAnnotation) {
                    val reuseId = "court"
                    val existing = mapView.dequeueReusableAnnotationViewWithIdentifier(reuseId)
                    val view = (existing as? MKMarkerAnnotationView)
                        ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = reuseId)
                    view.annotation = viewForAnnotation
                    view.clusteringIdentifier = CLUSTER_ID
                    view.canShowCallout = false

                    val court = viewForAnnotation.court
                    val sessionCount = sessionCountRef.value[court.id] ?: 0
                    val hasSession = sessionCount > 0
                    val hasTennis = court.sports.contains(Sport.TENNIS)
                    val hasPadel = court.sports.contains(Sport.PADEL)

                    view.glyphText = when {
                        hasTennis && hasPadel -> "🎾🏸"
                        hasPadel -> "🏸"
                        else -> "🎾"
                    }
                    view.markerTintColor = if (hasSession) limeColor else greenColor
                    return view
                }

                return null
            }

            override fun mapView(
                mapView: MKMapView,
                didSelectAnnotationView: MKAnnotationView
            ) {
                val ann = didSelectAnnotationView.annotation
                if (ann is CourtAnnotation) {
                    mapView.deselectAnnotation(ann, animated = true)
                    onCourtTapRef.value(ann.court)
                } else if (ann != null) {
                    mapView.deselectAnnotation(ann, animated = true)
                }
            }
        }
    }

    val cityRef = rememberUpdatedState(city)

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                showsUserLocation = false
                val (lat, lng) = CITY_CENTERS_IOS[city] ?: Pair(52.2297, 21.0122)
                val center = CLLocationCoordinate2DMake(lat, lng)
                setRegion(MKCoordinateRegionMakeWithDistance(center, 12000.0, 12000.0), animated = false)
            }
        },
        update = { mapView ->
            val (lat, lng) = CITY_CENTERS_IOS[cityRef.value] ?: Pair(52.2297, 21.0122)
            val center = CLLocationCoordinate2DMake(lat, lng)
            mapView.setRegion(MKCoordinateRegionMakeWithDistance(center, 12000.0, 12000.0), animated = true)
            mapView.removeAnnotations(mapView.annotations)
            courts.forEach { court ->
                val annotation = CourtAnnotation(court)
                annotation.setCoordinate(CLLocationCoordinate2DMake(court.lat, court.lng))
                mapView.addAnnotation(annotation)
            }
        }
    )
}
