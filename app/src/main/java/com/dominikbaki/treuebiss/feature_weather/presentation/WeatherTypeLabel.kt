package com.dominikbaki.treuebiss.feature_weather.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType

/**
 * Übersetzt eine [WeatherType] in einen anzeigbaren Text.
 *
 * Liegt in der UI-Schicht, damit das Domain-Modell frei von deutschen
 * Anzeigetexten bleibt.
 */
@Composable
@ReadOnlyComposable
internal fun weatherTypeLabel(type: WeatherType): String {
    val base = stringResource(
        when (type) {
            is WeatherType.ClearSky -> R.string.weather_type_clear_sky
            is WeatherType.MainlyClear -> R.string.weather_type_mainly_clear
            is WeatherType.PartlyCloudy -> R.string.weather_type_partly_cloudy
            is WeatherType.Overcast -> R.string.weather_type_overcast
            is WeatherType.Fog -> R.string.weather_type_fog
            is WeatherType.Rain -> R.string.weather_type_rain
            is WeatherType.Snow -> R.string.weather_type_snow
            is WeatherType.Thunderstorm -> R.string.weather_type_thunderstorm
            is WeatherType.Unknown -> R.string.weather_type_unknown
        }
    )

    val intensity = when (type) {
        is WeatherType.Rain -> type.intensity
        is WeatherType.Snow -> type.intensity
        is WeatherType.Thunderstorm -> type.intensity
        else -> null
    } ?: return base

    return stringResource(R.string.weather_type_with_intensity, base, intensityLabel(intensity))
}

@Composable
@ReadOnlyComposable
private fun intensityLabel(intensity: WeatherType.Intensity): String = stringResource(
    when (intensity) {
        WeatherType.Intensity.Light -> R.string.weather_intensity_light
        WeatherType.Intensity.Moderate -> R.string.weather_intensity_moderate
        WeatherType.Intensity.Heavy -> R.string.weather_intensity_heavy
        WeatherType.Intensity.Freezing -> R.string.weather_intensity_freezing
    }
)
