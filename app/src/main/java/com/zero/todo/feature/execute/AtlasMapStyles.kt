package com.zero.todo.feature.execute

/**
 * Cinema-grade architectural 3D map styles for ATLAS 2.0.
 * Local street-number noise, parcel tags, and commercial POI pins are suppressed so
 * sculpted 3D buildings, rivers, major arteries, and 3D quest beacons dominate cleanly.
 */
object AtlasMapStyles {

    val OBSIDIAN_DARK_STYLE_JSON: String = """
        [
          {"elementType":"geometry","stylers":[{"color":"#09090B"}]},
          {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"elementType":"labels.text.fill","stylers":[{"color":"#A0A0A0"}]},
          {"elementType":"labels.text.stroke","stylers":[{"color":"#101010"}]},
          {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#303030"}]},
          {"featureType":"administrative.land_parcel","elementType":"labels","stylers":[{"visibility":"off"}]},
          {"featureType":"landscape.natural","elementType":"geometry.fill","stylers":[{"color":"#181818"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"#202020"}]},
          {"featureType":"landscape.man_made","elementType":"geometry.stroke","stylers":[{"color":"#373737"}]},
          {"featureType":"poi","stylers":[{"visibility":"off"}]},
          {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#191919"}]},
          {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#303030"}]},
          {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#121212"}]},
          {"featureType":"road.local","elementType":"geometry.fill","stylers":[{"color":"#2C2C2C"}]},
          {"featureType":"road.local","elementType":"labels","stylers":[{"visibility":"off"}]},
          {"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#3D3D3D"}]},
          {"featureType":"road.arterial","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
          {"featureType":"road.arterial","elementType":"labels.text.fill","stylers":[{"color":"#B0B0B0"}]},
          {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#535353"}]},
          {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#212121"}]},
          {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#D5D5D5"}]},
          {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#292929"}]},
          {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#DEDEDE"}]},
          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0D0D0D"}]},
          {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#868686"}]}
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
