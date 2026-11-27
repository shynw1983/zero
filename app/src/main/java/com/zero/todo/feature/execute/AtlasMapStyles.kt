package com.zero.todo.feature.execute

/**
 * Cinema-grade architectural 3D map styles for ATLAS 2.0.
 * Local street-number noise, parcel tags, and commercial POI pins are suppressed so
 * sculpted 3D buildings, rivers, major arteries, and 3D quest beacons dominate cleanly.
 */
object AtlasMapStyles {

    val OBSIDIAN_DARK_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#0F141E"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#8C9BB5"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#0D121B"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#2B364B"}]},
          {"featureType":"administrative.land_parcel","elementType":"labels","stylers":[{"visibility":"off"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#222C3E"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#384866"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#102824"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#1E2738"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#0C1018"}]},
          {"featureType":"road.local","elementType":"geometry.fill","stylers":[{"color":"#1A2232"}]},
          {"featureType":"road.local","elementType":"labels","stylers":[{"visibility":"off"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#2C3952"}]},
          {"featureType":"road.arterial","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"featureType":"road.arterial","elementType":"labels.text.fill","stylers":[{"color":"#9AA9C2"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#3E5275"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1A2436"}]},
          {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#C2D0E8"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#202B3F"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#DCE6F5"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0A2444"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5C8BB8"}]}
        ]
    """.trimIndent()

    val COOL_PAPER_LIGHT_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#EAEDF3"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#586375"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#CBD2DE"}]},
          {"featureType":"administrative.land_parcel","elementType":"labels","stylers":[{"visibility":"off"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#F9FAFC"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#CDD4E0"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#D7E8DC"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#D8DFE9"}]},
          {"featureType":"road.local","elementType":"geometry.fill","stylers":[{"color":"#FAFBFD"}]},
          {"featureType":"road.local","elementType":"labels","stylers":[{"visibility":"off"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#FFFFFF"}]},
          {"featureType":"road.arterial","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"featureType":"road.arterial","elementType":"labels.text.fill","stylers":[{"color":"#4A5568"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#E2E8F4"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#BFC8DA"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#E3E8F0"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#334155"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#BDD8F0"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4C7396"}]}
        ]
    """.trimIndent()
}
