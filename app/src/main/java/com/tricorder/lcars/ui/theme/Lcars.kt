package com.tricorder.lcars.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tricorder.lcars.R

/** Classic TNG-era LCARS palette. */
object LcarsColors {
    val Black = Color(0xFF000000)
    val Orange = Color(0xFFFF9900)
    val Peach = Color(0xFFFF9966)
    val Sunset = Color(0xFFFF7755)
    val Tan = Color(0xFFFFCC99)
    val Yellow = Color(0xFFFFCC66)
    val Lilac = Color(0xFFCC99CC)
    val Purple = Color(0xFF9977AA)
    val Periwinkle = Color(0xFF9999FF)
    val Blue = Color(0xFF5588FF)
    val Ice = Color(0xFF99CCFF)
    val Mint = Color(0xFF66FFCC)
    val Red = Color(0xFFCC4444)
    val Text = Color(0xFFFFE6CC)
    val Dim = Color(0xFF665544)
}

/** Antonio is a condensed OFL face that closely matches on-screen LCARS lettering. */
@OptIn(ExperimentalTextApi::class)
val LcarsFont = FontFamily(
    Font(
        R.font.antonio,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.antonio,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.antonio,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

object LcarsType {
    val Title = TextStyle(
        fontFamily = LcarsFont, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, letterSpacing = 1.5.sp, color = LcarsColors.Orange,
    )
    val Button = TextStyle(
        fontFamily = LcarsFont, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, letterSpacing = 1.sp, color = LcarsColors.Black,
    )
    val Label = TextStyle(
        fontFamily = LcarsFont, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, letterSpacing = 1.sp, color = LcarsColors.Tan,
    )
    val Readout = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, color = LcarsColors.Text,
    )
    val BigReadout = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, color = LcarsColors.Yellow,
    )
    val Small = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, color = LcarsColors.Tan,
    )
}
